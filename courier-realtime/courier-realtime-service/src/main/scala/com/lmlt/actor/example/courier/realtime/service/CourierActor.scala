package com.lmlt.actor.example.courier.realtime.service

import java.time.Instant

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.lmlt.actor.example.courier.realtime.service.message.KinesisMessage.KinesisMessagePayload
import com.lmlt.actor.example.courier.realtime.service.message.{CourierActorEvt, CourierActorLocationEvt, CourierActorOfflineCmd, CourierActorState, CourierActorStatusEvt, CourierLocationMessage, CourierStatus, CourierStatusMessage, KinesisMessage, LocationPing}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object CourierActor {
  val conf = ConfigFactory.load()
  val shardRegion = conf.getString("application.courier-cluster.region")
  val shardNr = conf.getInt("application.courier-cluster.shardNr")
  val snapshotAfterMessageNr = conf.getInt("application.courier-cluster.snapshotAfterMessageNr")
  val offlineAfterS = conf.getLong("application.courier.offlineAfterS")
  val shouldPublish = conf.getBoolean("application.courier.publish")

  def props(gridMaster: GridMaster): Props = Props(new CourierActor(gridMaster))

  def cluster(gridMaster: GridMaster)(implicit system: ActorSystem): ActorRef =
    ClusterSharding(system).start(
      typeName = shardRegion,
      entityProps = props(gridMaster),
      settings = ClusterShardingSettings(system),
      extractEntityId = {
        case msg@KinesisMessage(_, KinesisMessagePayload.LocationPing(LocationPing(_, _, id, _))) => (id, msg)
      },
      extractShardId = {
        case KinesisMessage(_, KinesisMessagePayload.LocationPing(LocationPing(_, _, id, _))) =>
          (id.hashCode % shardNr).toString
        case ShardRegion.StartEntity(entityId) => (entityId.hashCode % shardNr).toString
      }
    )
}

class CourierActor(gridMaster: GridMaster) extends PersistentActor with ActorLogging {

  import CourierActor._
  import context._

  val courierId: String = self.path.name
  val mediator = DistributedPubSub(system).mediator

  var cancellable: Cancellable = _

  var state: CourierActorState = CourierActorState(courierStatus = CourierStatus.OFFLINE, lastMessageTimestamp = Long.MinValue)
  var count: Long = 0

  def publish(msg: Any): Unit = {
    if (shouldPublish) {
      mediator ! Publish(WSMessageActor.courierMessageTopic, msg)
    }
  }

  override def persistenceId: String = courierId

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      cancellable = system.scheduler.scheduleOnce(offlineAfterS second, self, CourierActorOfflineCmd())
      mediator ! Subscribe(WSMessageActor.courierReportTopic, self)
    case event: CourierActorEvt => updateState(event)
    case SnapshotOffer(_, snapshot) => state = snapshot.asInstanceOf[CourierActorState]
  }

  override def receiveCommand: Receive = {
    case WSMessageActor.reportCourierCmd =>
      sender() ! CourierStatusMessage(courierId = courierId, courierStatus = state.courierStatus)
      sender() ! CourierLocationMessage(courierId = courierId, coordinates = state.coordinates)
    case KinesisMessage(_, KinesisMessagePayload.LocationPing(LocationPing(traceId, timestamp, id, coordinates))) =>
      if (id != courierId) {
        log.error(s"Courier ID mismatch.  Expected $courierId but was $id.")
      } else {
        if (timestamp > state.lastMessageTimestamp) {
          if (cancellable != null) {
            cancellable.cancel()
            cancellable = system.scheduler.scheduleOnce(offlineAfterS second, self, CourierActorOfflineCmd())
          }
          val diff = Instant.now.toEpochMilli - timestamp
          log.info(s"traceId = $traceId, timeDiffMs = $diff, courierId = $id, coordinates = $coordinates")
          count += 1
          if (count % snapshotAfterMessageNr == 0) {
            saveSnapshot(state)
          }
          if (state.courierStatus == CourierStatus.OFFLINE) {
            persistAsync(CourierActorStatusEvt(timestamp, courierStatus = CourierStatus.ONLINE)) { event =>
              updateState(event)
              val msg = CourierStatusMessage(
                courierId = courierId,
                courierStatus = event.courierStatus,
                prevCoordinates = state.coordinates
              )
              publish(msg)
              gridMaster.updateCourierStatus(msg)
            }
          }
          persistAsync(CourierActorLocationEvt(timestamp, coordinates)) { event =>
            val prevCoordinates = state.coordinates
            updateState(event)
            val msg = CourierLocationMessage(
              courierId = courierId,
              coordinates = event.coordinates,
              prevCoordinates = prevCoordinates
            )
            publish(msg)
            gridMaster.updateCourierLocation(msg)
          }
        } else {
          log.info(s"Ignore KinesisMessage with timestamp $timestamp")
        }
      }
    case CourierActorOfflineCmd() =>
      count += 1
      if (count % snapshotAfterMessageNr == 0) {
        saveSnapshot(state)
      }
      if (state.courierStatus == CourierStatus.ONLINE) {
        persistAsync(CourierActorStatusEvt(courierStatus = CourierStatus.OFFLINE)) { event =>
          updateState(event)
          val msg = CourierStatusMessage(
            courierId = courierId,
            courierStatus = event.courierStatus,
            prevCoordinates = state.coordinates
          )
          publish(msg)
          gridMaster.updateCourierStatus(msg)
        }
      }
  }

  def updateState(evt: CourierActorEvt): Unit = evt match {
    case CourierActorStatusEvt(timestamp, courierStatus) if courierStatus == CourierStatus.ONLINE =>
      state = state.copy(lastMessageTimestamp = timestamp, courierStatus = courierStatus)
    case CourierActorStatusEvt(_, courierStatus) if courierStatus == CourierStatus.OFFLINE =>
      state = state.copy(courierStatus = courierStatus)
    case CourierActorLocationEvt(timestamp, coordinates) => state = state.copy(lastMessageTimestamp = timestamp, coordinates = coordinates)
  }
}
