package com.lmlt.actor.example

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.http.management.ClusterHttpManagement
import akka.cluster.sharding.ShardRegion.{ClusterShardingStats, GetClusterShardingStats}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Sink, Source}
import com.lmlt.actor.example.message.{First, HelloEvt, HelloState, Loop}
import kamon.Kamon

import scala.concurrent.duration._

object Main2 {

  def main(args: Array[String]): Unit = {
    Kamon.loadReportersFromConfig()

    implicit val system = ActorSystem("cluster-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future onFailure in the end
    implicit val executionContext = system.dispatcher
    val numberOfShards = system.settings.config.getInt("application.numberOfShards")

    // Kinesis consumer slave cluster
    val cluster = ClusterSharding(system).start(
      typeName = "hello",
      entityProps = Props[HelloActor].withDispatcher("kinesis-consumer-slave-dispatcher"),
      settings = ClusterShardingSettings(system),
      extractEntityId = {
        case msg@First(_, name) => (name, msg)
      },
      extractShardId = {
        case First(shard, _) => shard
        case ShardRegion.StartEntity(id) => String.valueOf(Integer.valueOf(id) % numberOfShards)
      }
    )

    system.actorOf(Props(new InitActor(cluster, numberOfShards)), name = "init")

    //    for (i <- 0 to 10) {
    //      cluster ! First(String.valueOf(i % numberOfShards), String.valueOf(i))
    //    }

    ClusterHttpManagement(Cluster(system)).start()

    val (source, wsActor) = RunWithHub.source(Source.actorRef(10, OverflowStrategy.dropHead))

    system.actorOf(Props(new ShardStatsActor(cluster, wsActor)), name = "shard-stats")

    val wsHandler = Flow.fromSinkAndSource(Sink.ignore, source)

    val wsRoute: Route = path("hello") {
      handleWebSocketMessages(wsHandler)
    }

    val bindingFuture = Http().bindAndHandle(wsRoute, "localhost", system.settings.config.getInt("application.websocket.port"))

    bindingFuture.onFailure {
      case ex: Exception =>
        println("Failed to bind to localhost:3000!")
    }
  }
}

class InitActor(cluster: ActorRef, numberOfShards: Int) extends Actor with ActorLogging {
  override def preStart(): Unit = {
    for (i <- 0 to 10) {
      cluster ! First(String.valueOf(i % numberOfShards), String.valueOf(i))
    }
  }

  override def receive: Receive = {
    case _ => log.info("Ignore incoming message")
  }
}

//case class First(shard: String, name: String)

//case object Loop

//case class HelloEvt(num: Long)

//case class HelloState(count: Long) {
//  def update(evt: HelloEvt): HelloState = copy(count + evt.num)
//}

class HelloActor extends PersistentActor with ActorLogging {

  import context._

  val loop = Loop()

  val name: String = self.path.name
  var shard: String = String.valueOf(Integer.valueOf(name) % system.settings.config.getInt("application.numberOfShards"))

  var state = HelloState(0)

  override def persistenceId: String = name

  override def preStart(): Unit = {
    system.scheduler.schedule(0 second, 5 second, self, loop)
  }

  override def receiveRecover: Receive = {
    case evt: HelloEvt => updateState(evt)
    case SnapshotOffer(_, snapshot: HelloState) => state = snapshot
  }

  override def receiveCommand: Receive = {
    case _: First => log.info("Ignore First message")
    case _: Loop => persist(HelloEvt(1))(updateState)
  }

  def updateState(event: HelloEvt): Unit = {
    state = HelloState(state.count + event.num)
    log.info(s"Hello: shard = $shard, name = $name, count = ${state.count}")
  }

  //  override def receive: Receive = {
  //    case First => log.info("Ignore First message")
  //    case Loop => {
  //      log.info(s"Hello: shard = $shard, name = $name")
  //    }
  //  }
}

class ShardStatsActor(cluster: ActorRef, wsActor: ActorRef) extends Actor with ActorLogging {

  import context._

  override def preStart(): Unit = {
    val getClusterShardingStats = GetClusterShardingStats(5 second)
    system.scheduler.schedule(0 second, 5 second, cluster, getClusterShardingStats)
  }

  override def receive: Receive = {
    case ClusterShardingStats(regions) => {
      val str = regions.map {
        case (address, stats) => {
          val statsString = stats.stats.map {
            case (shard, entities) => s"$shard:$entities"
          }.mkString(",")
          s"${address.hostPort}:{$statsString}"
        }
      }.mkString(",")
      wsActor ! TextMessage(s"{$str}")
    }
  }
}

object RunWithHub {
  def source[A, M](normal: Source[A, M])(implicit fm: Materializer, system: ActorSystem): (Source[A, NotUsed], M) = {
    val (normalMat, hubSource) = normal.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both).run
    (hubSource, normalMat)
  }
}