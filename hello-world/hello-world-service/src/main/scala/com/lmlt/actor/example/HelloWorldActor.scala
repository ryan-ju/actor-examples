package com.lmlt.actor.example

import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}

case class Hello(happiness: Int, time: ZonedDateTime)

case class ReportHello(happy: Boolean)

case class HelloResult(name: String, happy: Boolean, time: ZonedDateTime, count: Long)

object HelloWorldActor {
}

class HelloWorldActor(name: String) extends Actor with ActorLogging {
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("hello-report", self)

  var currentHappiness: Option[Int] = None

  var currentTime: Option[ZonedDateTime] = None

  val counter = new AtomicLong(0)

  var happy: Boolean = false

  override def receive: Receive = {
    case Hello(h, t) => {
      if (currentTime.isEmpty || t.isAfter(currentTime.get)) {
        currentHappiness match {
          case Some(prevHap) => {
            if (h - prevHap > 10) happy = true
            else if (prevHap - h > 10) happy = false
          }
          case None => // Do nothing
        }
        currentTime = Option(t)
        currentHappiness = Option(h)
        counter.incrementAndGet()
      }
    }
    case SubscribeAck(Subscribe("hello-report", None, `self`)) ⇒
      log.info("subscribing")
    case ReportHello(h) => {
      if (h == happy && currentTime.isDefined) {
        sender() ! HelloResult(name, happy, currentTime.get, counter.get())
      }
    }
  }
}