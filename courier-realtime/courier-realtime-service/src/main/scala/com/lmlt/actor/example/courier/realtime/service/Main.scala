package com.lmlt.actor.example.courier.realtime.service

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Origin`
import akka.http.scaladsl.server.Directives.{complete, concat, get, handleWebSocketMessages, onComplete, onSuccess, parameters, path}
import akka.http.scaladsl.server.PathMatcher._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import com.lmlt.actor.example.courier.realtime.service.message.Coordinates
import kamon.Kamon

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main {
  def main(args: Array[String]): Unit = {
    Kamon.loadReportersFromConfig()

    implicit val system = ActorSystem("cluster-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future onFailure in the end
    implicit val executionContext = system.dispatcher
    val conf = system.settings.config

    val gridMaster = new GridMaster()

    val (source, wsObserver) = Util.source(Source.actorRef(10, OverflowStrategy.dropHead))

    val wsMessage: ActorRef = system.actorOf(WSMessageActor.props(listener = wsObserver, gridMaster), name = "ws-message")

    val courierCluster = CourierActor.cluster(gridMaster)

    val placeActor = system.actorOf(PlaceActor.props().withDispatcher("http-dispatcher"), name = "place")

    val kinesisMessageRouter = system.actorOf(KinesisMessageRouter.props(locationPingActor = courierCluster), "kinesis-message-router")

    val kinesisCluster = system.actorOf(KinesisConsumerMaster.props(
      awsRegion = conf.getString("application.region"),
      streamName = conf.getString("application.kinesis-consumer-cluster.streamName"),
      akkaShardRegion = conf.getString("application.kinesis-consumer-cluster.region"),
      akkaShardsNr = conf.getInt("application.kinesis-consumer-cluster.shardNr"),
      snapshotAfterMessageNr = conf.getInt("application.kinesis-consumer-cluster.snapshotAfterMessageNr"),
      processorActor = kinesisMessageRouter
    ), "kinesis-consumer-master")

    wsMessage ! WSMessageBootstrapCmd(kinesisCluster, courierCluster)

    val wsHandler = Flow.fromSinkAndSource(Sink.actorRef(wsMessage, null), source)

    import JSONSupport._
    val route: Route = concat(
      path("healthcheck") {
        get {
          complete("healthy")
        }
      },
      path("service") {
        handleWebSocketMessages(wsHandler)
      },
      path("service" / "places") {
        get {
          implicit val timeout = Timeout(5 second)
          val future = (placeActor ? GetPlacesCmd).mapTo[PlacesMessage]
          onSuccess(future)(complete(201, List(`Access-Control-Allow-Origin`.*), _))
        }
      },
      path("service" / "recommendations") {
        get {
          parameters('longitude, 'latitude, 'radius, 'limit) { (longitude, latitude, radius, limit) =>
            val coordinates = Coordinates(longitude = longitude.toDouble, latitude = latitude.toDouble)
            val future = gridMaster.getCourierRecommendation(coordinates, radius.toLong, limit.toInt)
            onComplete(future) {
              case Success(recommendations) => complete(201, List(`Access-Control-Allow-Origin`.*), recommendations)

              case Failure(e) => {
                e.printStackTrace()
                complete(500, List(`Access-Control-Allow-Origin`.*), e.getMessage)
              }
            }
          }
        }
      }
    )

    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", system.settings.config.getInt("application.websocket.port"))

    bindingFuture.onFailure {
      case ex: Exception =>
        println("Failed to bind to localhost!")
    }
  }
}