package com.lmlt.actor.example.courier.realtime.service

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.ClusterShardingStats
import akka.http.scaladsl.model.ws.TextMessage
import akka.util.Timeout
import com.lmlt.actor.example.courier.realtime.service.message.{CourierLocationMessage, CourierStatusMessage}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

case class WSMessageBootstrapCmd(kinesisCluster: ActorRef, courierCluster: ActorRef)

case class ClusterShardingStatsMessage(prefix: String, stats: ClusterShardingStats)

object WSMessageActor {
  val host = ConfigFactory.load().getString("akka.remote.artery.canonical.hostname")

  val courierReportTopic = "courier-report"
  val courierMessageTopic = "courier-message"

  val reportCourierCmd = "report_courier"
  val reportHostCmd = "report_host"
  val reportKinesisClusterStatsCmd = "report_kinesis_cluster"
  val reportCourierClusterStatsCmd = "report_courier_cluster"

  val hostMessage = "host"
  val courierStatusMessage = "courier_status"
  val courierLocationMessage = "courier_location"
  val kinesisClusterStatsMessage = "kinesis_cluster_stats"
  val courierClusterStatsMessage = "courier_cluster_stats"

  val errorMessage = "error"

  def props(listener: ActorRef): Props =
    Props(new WSMessageActor(listener))

  def transformClusterShardRegionStats(prefix: String, stats: ClusterShardingStats): TextMessage = {
    val str = stats.regions.map {
      case (address, stats) => {
        val statsString = stats.stats.map {
          case (shard, entities) => s"$shard:$entities"
        }.mkString(",")
        s"${address.hostPort}->($statsString)"
      }
    }.mkString("|")
    TextMessage(s"$prefix:$str")
  }
}

class WSMessageActor(listener: ActorRef) extends Actor with ActorLogging {

  import WSMessageActor._
  import context._

  implicit val timeout = Timeout(5 second)

  val mediator = DistributedPubSub(system).mediator

  var kinesisCluster: ActorRef = _
  var courierCluster: ActorRef = _
  var kinesisClusterStats: ActorRef = _
  var courierClusterStats: ActorRef = _

  override def preStart(): Unit = {
    mediator ! Subscribe(WSMessageActor.courierMessageTopic, self)
  }

  override def receive: Receive = {
    case WSMessageBootstrapCmd(k, c) =>
      kinesisCluster = k
      courierCluster = c
      kinesisClusterStats = context.actorOf(Props(new ClusterStatsActor(kinesisClusterStatsMessage, kinesisCluster)), "kinesis-cluster-stats")
      courierClusterStats = context.actorOf(Props(new ClusterStatsActor(courierClusterStatsMessage, courierCluster)), "courier-cluster-stats")
    case x: TextMessage => x.getStrictText match {
      case `reportCourierCmd` => mediator ! Publish(courierReportTopic, reportCourierCmd)
      case `reportHostCmd` => listener ! TextMessage(s"$hostMessage:$host")
      case `reportKinesisClusterStatsCmd` => kinesisClusterStats ! ShardRegion.GetClusterShardingStats(5 second)
      case `reportCourierClusterStatsCmd` => courierClusterStats ! ShardRegion.GetClusterShardingStats(5 second)
    }
    case CourierStatusMessage(courierId, courierStatus) =>
      listener ! TextMessage(s"$courierStatusMessage:$courierId|$courierStatus")
    case CourierLocationMessage(courierId, Some(coordinates)) =>
      listener ! TextMessage(s"$courierLocationMessage:$courierId|${coordinates.longitude}|${coordinates.latitude}")
    case ClusterShardingStatsMessage(prefix, stats) =>
      listener ! transformClusterShardRegionStats(prefix, stats)
  }
}

class ClusterStatsActor(prefix: String, cluster: ActorRef) extends Actor {
  import context._

  override def receive: Receive = {
    case x: ShardRegion.GetClusterShardingStats =>
      cluster ! x
    case x: ClusterShardingStats => parent ! ClusterShardingStatsMessage(prefix, x)
  }
}
