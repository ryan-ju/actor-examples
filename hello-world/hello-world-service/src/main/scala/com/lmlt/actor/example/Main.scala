package com.lmlt.actor.example

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{complete, get, handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.Random

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("ClusterSystem")
    implicit val materializer = ActorMaterializer()
    // needed for the future onFailure in the end
    implicit val executionContext = system.dispatcher
    val numberOfShards = system.settings.config.getInt("application.numberOfShards")
    val port = system.settings.config.getInt("akka.remote.netty.tcp.port")
    println(s"+++++ port = $port")

    // Kinesis consumer slave cluster
    ClusterSharding(system).start(
      typeName = "kinesis-consumer-slave",
      entityProps = Props[KinesisConsumerSlave].withDispatcher("kinesis-consumer-slave-dispatcher"),
      settings = ClusterShardingSettings(system),
      extractEntityId = KinesisConsumerSlave.extractEntityId(numberOfShards),
      extractShardId = KinesisConsumerSlave.extractShardId(numberOfShards)
    )

    system.actorOf(Props(new KinesisConsumerMaster("hello-world-stream")))

//    val (queue, publisher) = Source.queue(10, OverflowStrategy.dropHead).toMat(Sink.asPublisher[Message](fanout = false))(Keep.both).run()
//
//    val wsHandler: Flow[Message, Message, Any] =
//      Flow.fromSinkAndSource(Sink.ignore, Source.fromPublisher(publisher))
//
//    val thread = new Thread(new Runnable {
//      override def run() = {
//        while(true) {
//          queue.offer(TextMessage(s"name${Math.abs(Random.nextInt()) % 10}|${Math.abs(Random.nextInt()) % 10}"))
//          TimeUnit.MILLISECONDS.sleep(1000)
//        }
//      }
//    })
//
//    thread.start()
//
//    val wsRoute: Route = path("hello") {
//      handleWebSocketMessages(wsHandler)
//    }
//
//    val bindingFuture = Http().bindAndHandle(wsRoute, "localhost", 3000)
//
//    bindingFuture.onFailure {
//      case ex: Exception =>
//        println("Failed to bind to localhost:3000!")
//    }
  }
}