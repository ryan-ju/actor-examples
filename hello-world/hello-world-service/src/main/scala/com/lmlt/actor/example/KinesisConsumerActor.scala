package com.lmlt.actor.example

import java.nio.charset.Charset

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import com.amazonaws.services.kinesis.model.{DescribeStreamRequest, GetRecordsRequest, GetRecordsResult, GetShardIteratorRequest, Shard}
import com.amazonaws.services.kinesis.{AmazonKinesis, AmazonKinesisClient}

import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.concurrent.duration._

case class Start(streamName: String, shard: Shard)
case class Poll(shardIterator: String)

class KinesisConsumerMaster(streamName: String) extends Actor with ActorLogging {

  val client: AmazonKinesis = AmazonKinesisClient.builder()
    .withRegion(context.system.settings.config.getString("application.region"))
    .build()

  override def preStart(): Unit = {
    // Retrieve all shards
    val describeStreamRequest = new DescribeStreamRequest()
      .withStreamName(streamName)
    val shards = mutable.Buffer[Shard]()
    var exclusiveStartShardId: String = null
    do {
      describeStreamRequest.setExclusiveStartShardId(exclusiveStartShardId)
      val describeStreamResult = client.describeStream(describeStreamRequest)
      shards ++= describeStreamResult.getStreamDescription.getShards.asScala
      if (describeStreamResult.getStreamDescription.getHasMoreShards && shards.nonEmpty)
        exclusiveStartShardId = shards.last.getShardId
      else exclusiveStartShardId = null
    } while (exclusiveStartShardId != null)

    // Create clustered slaves
    val kinesisConsumerSlaveRegion: ActorRef = ClusterSharding(context.system).shardRegion("kinesis-consumer-slave")
    for (shard <- shards) {
      kinesisConsumerSlaveRegion ! Start(streamName, shard)
    }
  }

  override def receive: Receive = {
    case x => log.info("Received: ", x)
  }
}

object KinesisConsumerSlave {
  val charset = Charset.forName("UTF-8")

  val extractEntityId: Int => ShardRegion.ExtractEntityId = (numberOfShards) => {
    case msg @ Start(_, shard) => (shard.getShardId, msg)
  }
  val extractShardId: Int => ShardRegion.ExtractShardId = (numberOfShards) => {
    case Start(_, shard) => (shard.getShardId.hashCode % numberOfShards).toString
    // TODO case ShardRegion.StartEntry(id)
  }
}

class KinesisConsumerSlave extends Actor with ActorLogging {
  import context._

  private val client = AmazonKinesisClient.builder()
    .withRegion(context.system.settings.config.getString("application.region"))
    .build()
  private var shard: Shard = _

  def poll: Receive = {
    case Poll(shardIterator) => {
        val getRecordsRequest = new GetRecordsRequest()
        .withShardIterator(shardIterator)
        .withLimit(25)
      val result = client.getRecords(getRecordsRequest)
      for (record <- result.getRecords.asScala) {
          log.info(s"Poll received message with shardId = ${shard.getShardId}, partitionKey = ${record.getPartitionKey}, data = ${KinesisConsumerSlave.charset.decode(record.getData).toString}")
      }
      context.system.scheduler.scheduleOnce(1 second, self, Poll(result.getNextShardIterator))
    }
    case x => {
      log.info(s"Ignore message ${x.getClass.getSimpleName}")
    }
  }

  override def receive: Receive = {
    case Start(streamName, shard) => {
      this.shard = shard
      log.info(s"Received message with streamName = ${streamName}, shardId = ${shard.getShardId}")
      val getShardIteratorRequest = new GetShardIteratorRequest()
        .withStreamName(streamName)
        .withShardId(shard.getShardId)
        .withShardIteratorType("TRIM_HORIZON")
      val result = client.getShardIterator(getShardIteratorRequest)
      context.become(poll)
      self ! Poll(result.getShardIterator)
    }
  }
}

class DummyProcessor extends Actor with ActorLogging {
  override def receive: Receive = Actor.ignoringBehavior
}