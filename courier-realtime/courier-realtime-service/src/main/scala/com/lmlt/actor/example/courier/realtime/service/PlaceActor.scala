package com.lmlt.actor.example.courier.realtime.service

import akka.actor.{Actor, ActorLogging, Props}
import com.datastax.driver.core.Cluster
import com.lmlt.actor.example.courier.realtime.service.message.{Coordinates, CourierLocationMessage, Place}
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

case object GetPlacesCmd

case class PlacesMessage(places: Seq[Place])

case class GetCouriersCmd(radius: Int)

case class CourierMessage(couriers: List[CourierLocationMessage])

object PlaceActor {
  val conf = ConfigFactory.load()
  val cassandraCluster = conf.getStringList("application.cassandra.cluster").asScala
  val cassandraPort = conf.getInt("application.cassandra.port")
  val placeKeyspace = conf.getString("application.cassandra.place.keyspace")
  val placeTable = conf.getString("application.cassandra.place.columnFamily")
  def props(): Props = Props[PlaceActor]
}

class PlaceActor extends Actor with ActorLogging {

  import context._
  import PlaceActor._

  var placeMap: Map[String, Place] = _

  override def preStart(): Unit = {
    val cassandra = Cluster.builder()
      .addContactPoints(cassandraCluster:_*)
      .withPort(cassandraPort)
      .build()

    val session = cassandra.connect(placeKeyspace)
    val resultSet = session.execute(s"SELECT * FROM $placeTable").asScala
    placeMap = resultSet.map { row =>
      val placeId = row.getString("place_id")
      val longitude = row.getDecimal("longitude")
      val latitude = row.getDecimal("latitude")
      val coordinates = Coordinates(longitude = longitude.doubleValue(), latitude = latitude.doubleValue())
      (placeId -> Place(placeId, Option(coordinates)))
    }.toMap

    session.close()
    cassandra.close()
  }

  override def receive: Receive = {
    case GetPlacesCmd => sender() ! PlacesMessage(placeMap.values.toSeq)
  }
}
