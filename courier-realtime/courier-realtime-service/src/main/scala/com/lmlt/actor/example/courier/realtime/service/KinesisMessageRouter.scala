package com.lmlt.actor.example.courier.realtime.service

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.lmlt.actor.example.courier.realtime.service.message.KinesisMessage
import com.lmlt.actor.example.courier.realtime.service.message.KinesisMessage.KinesisMessagePayload

case class KinesisMessageRouterFailedMessage(msg: String)

object KinesisMessageRouter {
  def props(locationPingActor: ActorRef): Props =
    Props(new KinesisMessageRouter(locationPingActor))
}

class KinesisMessageRouter(locationPingActor: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case msg @ KinesisMessage(_, payload) => payload match {
      case _: KinesisMessagePayload.LocationPing => locationPingActor ! msg
      case x => log.error(s"Cannot route message with payload of type ${x.getClass.getSimpleName}")
    }
    case _ => sender ! KinesisMessageRouterFailedMessage("Cannot route non KinesisMessage message")
  }
}
