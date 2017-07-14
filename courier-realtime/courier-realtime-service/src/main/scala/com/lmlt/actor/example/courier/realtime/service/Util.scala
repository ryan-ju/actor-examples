package com.lmlt.actor.example.courier.realtime.service

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}

object Util {
  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
  def timestamp(): Long = Instant.now.toEpochMilli
  def traceId(): String = "trace-id:" + UUID.randomUUID().toString
  def source[A, M](normal: Source[A, M])(implicit fm: Materializer): (Source[A, NotUsed], M) = {
    val (normalMat, hubSource) = normal.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both).run
    (hubSource, normalMat)
  }
}
