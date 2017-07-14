package com.lmlt.actor.example.courier.realtime.service

import akka.pattern.ask
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.cluster.sharding.ShardRegion.ClusterShardingStats
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import com.lmlt.actor.example.courier.realtime.service.GridMaster.conf
import com.lmlt.actor.example.courier.realtime.service.message.{Coordinates, CourierDistanceMessage, CourierListCmd, CourierListMessage, CourierLocationMessage, CourierRecommendationMessage, CourierStatus, CourierStatusMessage, GridActorCourierEnterEvt, GridActorCourierLeaveEvt, GridActorEvt, GridActorState, GridCoordinates, GridCourierEnterCmd, GridCourierLeaveCmd, GridCourierStatusCmd}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

object GridMaster {
  val conf = ConfigFactory.load()
  val shardRegion = conf.getString("application.grid-cluster.region")
  val shardNr = conf.getInt("application.grid-cluster.shardNr")
  val gridSize = conf.getLong("application.gridSize")
}

class GridMaster(implicit system: ActorSystem) {

  import GridMaster._

  implicit val timeout = Timeout(5 second)
  implicit val executionContext = system.dispatcher

  val gridActor: ActorRef = ClusterSharding(system).start(
    typeName = shardRegion,
    entityProps = GridSlave.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = GridSlave.extractEntityId,
    extractShardId = GridSlave.extractShardId(shardNr)
  )

  def getCourierRecommendation(coordinates: Coordinates, radius: Long, limit: Int): Future[CourierRecommendationMessage] = {
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be positive")
    }
    val x = coordinates.longitude.toLong
    val y = coordinates.latitude.toLong
    val futures: mutable.Buffer[Future[CourierListMessage]] = mutable.Buffer()
    ((x - radius + 1) to (x + radius - 1)).foreach { i =>
      ((y - radius + 1) to (y + radius - 1)).foreach { j =>
        if (0 <= i && i < gridSize && 0 <= j && j < gridSize) {
          futures += (gridActor ? CourierListCmd(limit, Option(coordinates), Option(GridCoordinates(x = i, y = j)))).mapTo[CourierListMessage]
        }
      }
    }

    Future.sequence(futures).map { buf =>
      val courierDistanceMessages = buf
        .flatMap(_.couriers)
        .sortBy(_.distance)
        .take(limit)
      val grids = buf
        .map(_.grid.get)
      CourierRecommendationMessage(couriers = courierDistanceMessages, grids = grids)
    }
  }

  def updateCourierStatus(msg: CourierStatusMessage): Unit = {
    if (msg.prevCoordinates.isDefined) {
      val x = msg.prevCoordinates.get.longitude.toLong
      val y = msg.prevCoordinates.get.latitude.toLong
      val grid = GridCoordinates(x = x, y = y)
      gridActor ! GridCourierStatusCmd(Option(msg), Option(grid))
    }
  }

  def updateCourierLocation(msg: CourierLocationMessage): Unit = {
    if (msg.prevCoordinates.isDefined) {
      val prevX = msg.prevCoordinates.get.longitude.toLong
      val prevY = msg.prevCoordinates.get.latitude.toLong
      val x = msg.coordinates.get.longitude.toLong
      val y = msg.coordinates.get.latitude.toLong
      if (x != prevX || y != prevY) { // Courier has moved across grid square
        val prevGrid = GridCoordinates(x = prevX, y = prevY)
        val grid = GridCoordinates(x = x, y = y)
        gridActor ! GridCourierLeaveCmd(Option(msg), Option(prevGrid))
        gridActor ! GridCourierEnterCmd(Option(msg), Option(grid))
      }
    } else {
      val x = msg.coordinates.get.longitude.toLong
      val y = msg.coordinates.get.latitude.toLong
      val grid = GridCoordinates(x = x, y = y)
      gridActor ! GridCourierEnterCmd(Option(msg), Option(grid))
    }
  }

  def getClusterStats(): Future[ClusterShardingStats] =
    (gridActor ? ShardRegion.GetClusterShardingStats(5 second)).mapTo[ClusterShardingStats]

}

case class GridCourierLeaveScheduledCmd(courierId: String)

object GridSlave {
  val snapshotAfterMessageNr = conf.getInt("application.grid-cluster.snapshotAfterMessageNr")

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg@CourierListCmd(_, _, Some(grid)) => (gridToString(grid), msg)
    case msg@GridCourierStatusCmd(_, Some(grid)) => (gridToString(grid), msg)
    case msg@GridCourierEnterCmd(_, Some(grid)) => (gridToString(grid), msg)
    case msg@GridCourierLeaveCmd(_, Some(grid)) => (gridToString(grid), msg)
  }

  val extractShardId: Int => ShardRegion.ExtractShardId = (numberOfShards) => {
    case CourierListCmd(_, _, Some(grid)) => (gridToString(grid).hashCode % numberOfShards).toString
    case GridCourierStatusCmd(_, Some(grid)) => (gridToString(grid).hashCode % numberOfShards).toString
    case GridCourierEnterCmd(_, Some(grid)) => (gridToString(grid).hashCode % numberOfShards).toString
    case GridCourierLeaveCmd(_, Some(grid)) => (gridToString(grid).hashCode % numberOfShards).toString
    case ShardRegion.StartEntity(entityId) => (entityId.hashCode % numberOfShards).toString
  }

  def gridToString(grid: GridCoordinates): String = s"${grid.x}-${grid.y}"

  def props(): Props = Props[GridSlave]

  def distance(coordinates: Coordinates, origin: Coordinates): Double = {
    val lng = coordinates.longitude - origin.longitude
    val lat = coordinates.latitude - origin.latitude
    lng * lng + lat * lat
  }
}

class GridSlave extends PersistentActor with ActorLogging {

  import GridSlave._
  import context._

  var state: GridActorState = GridActorState(Map())

  var cancellables: Map[String, Cancellable] = Map()

  var count: Long = 0

  override def persistenceId: String = self.path.name

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      state.coordinates.keys.foreach { courierId =>
        // TODO: this is 24 hours in the future because of the following constraints
        // 1. GridActor only receives a GridCourierEnterCmd message when a courier enters, not when a courier is already inside.
        // 2. When system crashes, there is a risk that a GridCourierLeaveCmd is never received, which if not cleaned up will cause a resource leak.
        // This means that if a courier stays inside one square for over 24 hours then he will be removed from the system, until he moves to another square.
        // This means the courier will not show up in the courier recommendation (false negative).
        // On the other hand, if a courier has left the square but the message is missed, we may still give the courier in recommendations (false positive).
        // Hence, 24 hours is probably a good balance between false positive and false negative.
        cancellables += (courierId -> system.scheduler.scheduleOnce(24 hour, self, GridCourierLeaveScheduledCmd(courierId)))
      }
    case event: GridActorEvt => updateState(event)
    case SnapshotOffer(_, snapshot) => state = snapshot.asInstanceOf[GridActorState]
  }

  override def receiveCommand: Receive = {
    case CourierListCmd(limit, Some(origin), grid) =>
      val courierDistanceMessages = state.coordinates
        .map { case (courierId, coordinates) => (courierId, distance(coordinates, origin)) }
        .toSeq
        .sortBy(_._2) // Sort by distance
        .take(limit)
        .map { case (courierId, distance) => CourierDistanceMessage(courierId, distance) }
      sender() ! CourierListMessage(courierDistanceMessages, grid)
    case GridCourierStatusCmd(Some(evt), _) if evt.courierStatus == CourierStatus.ONLINE =>
      persistAsync(GridActorCourierEnterEvt(evt.courierId, evt.prevCoordinates)) { event =>
        updateState(event)
        count += 1
        if (count % snapshotAfterMessageNr == 0) {
          saveSnapshot(state)
        }
        if (cancellables.contains(event.courierId)) {
          cancellables(event.courierId).cancel()
        }
        cancellables += (event.courierId -> system.scheduler.scheduleOnce(24 hour, self, GridCourierLeaveScheduledCmd(event.courierId)))
      }
    case GridCourierStatusCmd(Some(evt), _) if evt.courierStatus == CourierStatus.OFFLINE =>
      persistAsync(GridActorCourierLeaveEvt(evt.courierId)) { event =>
        updateState(event)
        count += 1
        if (count % snapshotAfterMessageNr == 0) {
          saveSnapshot(state)
        }
        if (cancellables.contains(event.courierId)) {
          cancellables(event.courierId).cancel()
          cancellables -= event.courierId
        }
      }
    case GridCourierEnterCmd(Some(evt), _) =>
      persistAsync(GridActorCourierEnterEvt(evt.courierId, evt.coordinates)) { event =>
        updateState(event)
        count += 1
        if (count % snapshotAfterMessageNr == 0) {
          saveSnapshot(state)
        }
        if (cancellables.contains(event.courierId)) {
          cancellables(event.courierId).cancel()
        }
        cancellables += (event.courierId -> system.scheduler.scheduleOnce(24 hour, self, GridCourierLeaveScheduledCmd(event.courierId)))
      }
    case GridCourierLeaveCmd(Some(evt), _) =>
      persistAsync(GridActorCourierLeaveEvt(evt.courierId)) { event =>
        updateState(event)
        count += 1
        if (count % snapshotAfterMessageNr == 0) {
          saveSnapshot(state)
        }
        if (cancellables.contains(event.courierId)) {
          cancellables(event.courierId).cancel()
          cancellables -= event.courierId
        }
      }
    case GridCourierLeaveScheduledCmd(courierId) =>
      persistAsync(GridActorCourierLeaveEvt(courierId)) { event =>
        updateState(event)
        count += 1
        if (count % snapshotAfterMessageNr == 0) {
          saveSnapshot(state)
        }
        if (cancellables.contains(event.courierId)) {
          cancellables(event.courierId).cancel()
          cancellables -= event.courierId
        }
      }
  }

  def updateState(evt: GridActorEvt): Unit = evt match {
    case GridActorCourierEnterEvt(courierId, Some(coordinates)) =>
      if (!state.coordinates.contains(courierId) || state.coordinates(courierId) != coordinates) {
        state = state.copy(coordinates = state.coordinates + (courierId -> coordinates))
      }
    case GridActorCourierLeaveEvt(courierId) =>
      if (state.coordinates.contains(courierId)) {
        state = state.copy(coordinates = state.coordinates - courierId)
      }
  }
}
