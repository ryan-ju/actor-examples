package com.lmlt.actor.example.courier.realtime.service

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import kamon.Kamon

object Main {
  def main(args: Array[String]): Unit = {
    Kamon.loadReportersFromConfig()

    implicit val system = ActorSystem("cluster-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future onFailure in the end
    implicit val executionContext = system.dispatcher
    val conf = system.settings.config

    val (source, wsObserver) = Util.source(Source.actorRef(10, OverflowStrategy.dropHead))

    val wsMessage: ActorRef = system.actorOf(WSMessageActor.props(listener = wsObserver), name = "ws-message")

    val courierCluster = CourierActor.cluster(observer = wsMessage)

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

    val wsRoute: Route = path("events") {
      handleWebSocketMessages(wsHandler)
    }

    val bindingFuture = Http().bindAndHandle(wsRoute, "localhost", system.settings.config.getInt("application.websocket.port"))

    bindingFuture.onFailure {
      case ex: Exception =>
        println("Failed to bind to localhost!")
    }
  }
}