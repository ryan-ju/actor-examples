package com.lmlt.actor.example.courier.realtime.kinesis

import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path, parameter, concat, get, post, complete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.PathMatcher._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Origin`}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import com.amazonaws.services.kinesis.model.{PutRecordsRequest, PutRecordsRequestEntry}
import com.amazonaws.services.kinesis.{AmazonKinesis, AmazonKinesisClient}
import com.lmlt.actor.example.courier.realtime.service.message.KinesisMessage.KinesisMessagePayload
import com.lmlt.actor.example.courier.realtime.service.message.{Coordinates, KinesisMessage, LocationPing}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

/**
  * Created by ryan on 27/05/2017.
  */
object Main {
  val logger = LoggerFactory.getLogger(this.getClass)
  var count = 0

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("kinesis-injector")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val conf = ConfigFactory.load()
    val courierNr = conf.getInt("application.courier.nr")

    val client = AmazonKinesisClient.builder()
      .withRegion(conf.getString("application.region"))
      .build()

    // Injector actor
    val injector = system.actorOf(KinesisInjectorActor.props(
      client = client,
      streamName = conf.getString("application.kinesis.streamName"),
      batchSize = conf.getInt("application.kinesis.batchSize"),
      timeoutMs = conf.getInt("application.kinesis.timeoutMs")
    ), "kinesis-injector")

    val courierManager = system.actorOf(CourierManagerActor.props(injector), "courier-manager")

//    val (source, wsObserver) = Util.source(Source.actorRef(10, OverflowStrategy.dropHead))

//    val wsCommandActor = system.actorOf(WSCommandActor.props(courierManager, wsObserver), "ws-command")

    courierManager ! CourierManagerStartMessage(courierNr)

    // Websocket handler
//    val wsHandler = Flow.fromSinkAndSource(Sink.actorRef(wsCommandActor, null), source)

    val wsRoute: Route = concat(
//      path("injector") {
//        handleWebSocketMessages(wsHandler)
//      },
      path("injector" / "gridSize") {
        get {
          complete(201, List(`Access-Control-Allow-Origin`.*), conf.getString("application.grid.width"))
        }
      },
      path("injector" / "courierNr") {
        get {
          complete(201, List(`Access-Control-Allow-Origin`.*), conf.getString("application.courier.nr"))
        }
      },
      path("injector" / "startCourier") {
        post {
          parameter('courierId) { courierId =>
            courierManager ! WSMessage.createCourierManagerMessage(s"${WSMessage.courierManagerStartCourier}:$courierId")
            complete(201, List(`Access-Control-Allow-Origin`.*), s"Courier $courierId started")
          }
        }
      },
      path("injector" / "stopCourier") {
        post {
          parameter('courierId) { courierId =>
            courierManager ! WSMessage.createCourierManagerMessage(s"${WSMessage.courierManagerStopCourier}:$courierId")
            complete(201, List(`Access-Control-Allow-Origin`.*), s"Courier $courierId stopped")
          }
        }
      }
    )

    val bindingFuture = Http().bindAndHandle(wsRoute, "localhost", system.settings.config.getInt("application.websocket.port"))

    bindingFuture.onFailure {
      case ex: Exception =>
        println("Failed to bind to localhost!")
    }
  }

  def randomMessage(): String = {
    count += 1
    s"Seq: ${count}, Hello: ${Random.nextInt(1000)}"
  }
}

/*
Courier Manager
 */

sealed trait CourierManagerMessage

case class CourierManagerStartMessage(courierNr: Int) extends CourierManagerMessage

case class CourierManagerQueryMessage(listener: ActorRef) extends CourierManagerMessage

case class CourierManagerStartCourierMessage(courierId: String) extends CourierManagerMessage

case class CourierManagerStopCourierMessage(courierId: String) extends CourierManagerMessage

case class CourierManagerStateMessage(courierLocations: Map[String, CourierLocationMessage])

object CourierManagerActor {
  def props(injector: ActorRef): Props = Props(new CourierManagerActor(injector))
}

class CourierManagerActor(injector: ActorRef) extends Actor with ActorLogging {

  // Needed for actor ask
  implicit val timeout = Timeout(5 seconds)

  import context._

  val map: mutable.Map[String, ActorRef] = mutable.Map()

  override def receive: Receive = {
    case CourierManagerStartMessage(courierNr) =>
      for (i <- 0 until courierNr) {
        val courierId = s"courier-$i"
        val courierActor = actorOf(CourierActor.props(courierId), courierId)
        map += (courierId -> courierActor)
      }
    case CourierManagerStopCourierMessage(courierId) =>
      map.get(courierId) match {
        case Some(courierActor) =>
          courierActor ! CourierStopMessage
          sender() ! WSCommandMessage(s"Courier $courierId stopped")
        case _ => log.error(s"Stop courier: courier $courierId doesn't exist")
      }

    case CourierManagerStartCourierMessage(courierId) =>
      map.get(courierId) match {
        case Some(courierActor) =>
          courierActor ! CourierStartMessage
          sender() ! WSCommandMessage(s"Courier $courierId started")
        case _ => log.error(s"Start courier: courier $courierId doesn't exist")
      }
    case CourierManagerQueryMessage(listener) =>
      val futureSeq = map.toSeq.map { case (courierId, courierActor) =>
        val future = courierActor ? CourierQueryMessage
        future.map(x => courierId -> x.asInstanceOf[CourierLocationMessage])
      }
      Future.sequence(futureSeq).onComplete {
        case Success(value) => listener ! CourierManagerStateMessage(value.toMap)
        case Failure(failue) => log.error(failue, "Failed to gather courier locations")
      }
    case x: CourierLocationMessage => injector ! x
  }
}

/*
Courier
 */

sealed trait CourierMessage

case object CourierLoopMessage extends CourierMessage

case object CourierStopMessage extends CourierMessage

case object CourierStartMessage extends CourierMessage

case object CourierQueryMessage extends CourierMessage

case class CourierLocationMessage(id: String, x: Double, y: Double)

object CourierActor {
  val conf = ConfigFactory.load()
  val gridWidth = conf.getLong("application.grid.width")
  val gridHeight = conf.getLong("application.grid.height")
  val pingPeriodMs = conf.getLong("application.courier.pingPeriodMs")
  val step = conf.getDouble("application.courier.step")

  def props(id: String): Props = Props(new CourierActor(id))
}

class CourierActor(id: String) extends Actor with ActorLogging {

  import CourierActor._
  import context._

  var x: Double = ThreadLocalRandom.current().nextDouble(gridWidth)
  var y: Double = ThreadLocalRandom.current().nextDouble(gridHeight)
  var cancelled: Boolean = false
  // This is necessary to ensure cancel has been acknowledged by the scheduled message
  var cancelConfirmed: Boolean = false

  override def preStart(): Unit = {
    system.scheduler.scheduleOnce(ThreadLocalRandom.current().nextLong(pingPeriodMs) millisecond, self, CourierLoopMessage)
  }

  override def receive: Receive = {
    case CourierLoopMessage =>
      if (!cancelled) {
        x = Math.min(gridWidth, Math.max(0, ThreadLocalRandom.current().nextDouble(-step, step) + x))
        y = Math.min(gridHeight, Math.max(0, ThreadLocalRandom.current().nextDouble(-step, step) + y))
        parent ! CourierLocationMessage(id, x, y)
        system.scheduler.scheduleOnce(pingPeriodMs millisecond, self, CourierLoopMessage)
      } else {
        cancelConfirmed = true
      }
    case CourierStopMessage => cancelled = true
    case CourierStartMessage =>
      if (cancelled) {
        cancelled = false
        if (cancelConfirmed) {
          system.scheduler.scheduleOnce(pingPeriodMs millisecond, self, CourierLoopMessage)
        }
        cancelConfirmed = false
      }
    case CourierQueryMessage => sender() ! CourierLocationMessage(id, x, y)
  }
}

/*
Kinesis Injector
 */

sealed trait KinesisInjectorMessage

case object KinesisInjectorLoopMessage extends KinesisInjectorMessage

object KinesisInjectorActor {
  def props(client: AmazonKinesis, streamName: String, batchSize: Int, timeoutMs: Long): Props = Props(new KinesisInjectorActor(client, streamName, batchSize, timeoutMs))
}

class KinesisInjectorActor(client: AmazonKinesis, streamName: String, batchSize: Int, timeoutMs: Long) extends Actor with ActorLogging {

  import context._

  val batch: mutable.ArrayBuffer[CourierLocationMessage] = mutable.ArrayBuffer()
  var cancelHandle: Cancellable = _

  override def preStart(): Unit = {
    cancelHandle = system.scheduler.scheduleOnce(timeoutMs millisecond, self, KinesisInjectorLoopMessage)
  }

  override def receive: Receive = {
    case x: CourierLocationMessage =>
      batch += x
      if (batch.nonEmpty && batch.size % batchSize == 0) {
        cancelHandle.cancel()
        inject()
        cancelHandle = system.scheduler.scheduleOnce(timeoutMs millisecond, self, KinesisInjectorLoopMessage)
      }
    case KinesisInjectorLoopMessage =>
      if (batch.nonEmpty) {
        inject()
      }
      cancelHandle = system.scheduler.scheduleOnce(timeoutMs millisecond, self, KinesisInjectorLoopMessage)
  }

  def inject(): Unit = {
    val records = batch.map { message =>
      val pbMessage = KinesisMessage(kinesisMessagePayload = KinesisMessagePayload.LocationPing(
        LocationPing(
          traceId = Util.traceId(),
          timestamp = Util.timestamp(),
          courierId = message.id,
          coordinates = Option(Coordinates(latitude = message.y, longitude = message.x))
        )))
      new PutRecordsRequestEntry()
        .withPartitionKey(message.id)
        .withData(ByteBuffer.wrap(pbMessage.toByteArray))
    }
    val putRecordsRequest = new PutRecordsRequest()
      .withStreamName(streamName)
      .withRecords(records.asJavaCollection)
    val result = client.putRecords(putRecordsRequest)
    Main.logger.info("Put record result: failed count = {}, records = {}",
      Array(
        result.getFailedRecordCount,
        result.getRecords.asScala.map(x => s"shardId = ${x.getShardId}, seq no = ${x.getSequenceNumber}").toArray.mkString("\n")): _*
    )
    batch.clear()
  }
}

/*
Websocket Command Actor
 */
case class WSCommandMessage(confirmation: String)

object WSCommandActor {
  def props(courierManager: ActorRef, observer: ActorRef): Props =
    Props(new WSCommandActor(courierManager, observer))
}

class WSCommandActor(courierManager: ActorRef, observer: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case x: TextMessage =>
      val msg = x.getStrictText
      val cmd = WSMessage.createCourierManagerMessage(msg)
      courierManager ! cmd
    case WSCommandMessage(confirmation) => observer ! WSMessage.createAckMessage(confirmation)
    case x => log.error(s"Cannot process message of type ${x.getClass.getSimpleName}")
  }
}