package com.lmlt.actor.example.courier.realtime.kinesis

import akka.http.scaladsl.model.ws.TextMessage

object WSMessage {
  val ack = "ack"
  val courierLocation = "courier_location"
  val courierManagerStartCourier = "courier_manager_start_courier"
  val courierManagerStopCourier = "courier_manager_stop_courier"

  def courierLocation(id: String, x: Long, y: Long): TextMessage = {
    TextMessage(s"$courierLocation:$id|$x|$y")
  }
  def createAckMessage(input: String): TextMessage = {
    TextMessage(s"$ack:$input")
  }
  def createCourierManagerMessage(input: String): CourierManagerMessage = {
    val parts = input.split(":")
    val messageType = parts(0)
    messageType match {
      case `courierManagerStartCourier` =>
        val courierId = parts(1)
        CourierManagerStartCourierMessage(courierId)
      case `courierManagerStopCourier` =>
        val courierId = parts(1)
        CourierManagerStopCourierMessage(courierId)
    }
  }
}
