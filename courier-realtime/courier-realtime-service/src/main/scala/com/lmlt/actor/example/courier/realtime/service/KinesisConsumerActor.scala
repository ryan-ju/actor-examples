package com.lmlt.actor.example.courier.realtime.service

import akka.pattern.{ask, pipe}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import com.amazonaws.services.kinesis.model.{DescribeStreamRequest, GetRecordsRequest, GetShardIteratorRequest, Shard}
import com.amazonaws.services.kinesis.{AmazonKinesis, AmazonKinesisClient}
import com.lmlt.actor.example.courier.realtime.service.message.{KinesisConsumerActorState, KinesisConsumerBootstrapCmd, KinesisConsumerBootstrapEvt, KinesisConsumerEvt, KinesisConsumerLoopCmd, KinesisConsumerUpdateSeqNrEvt, KinesisMessage}
import org.apache.commons.lang3.StringUtils

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import kamon.Kamon
import kamon.util.MeasurementUnit

object KinesisConsumerMaster {
  def props[T](awsRegion: String, streamName: String, akkaShardRegion: String, akkaShardsNr: Int, snapshotAfterMessageNr: Long, processorActor: ActorRef): Props =
    Props(new KinesisConsumerMaster(awsRegion, streamName, akkaShardRegion, akkaShardsNr, snapshotAfterMessageNr, processorActor))
}

class KinesisConsumerMaster[T](awsRegion: String, streamName: String, akkaShardRegion: String, akkaShardsNr: Int, snapshotAfterMessageNr: Long, processorActor: ActorRef) extends Actor with ActorLogging {
  import context._
  implicit val timeout = Timeout(5 second)

  val client: AmazonKinesis = AmazonKinesisClient.builder()
    .withRegion(awsRegion)
    .build()

  var slaveCluster: ActorRef = _

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

    // Start cluster
    slaveCluster = ClusterSharding(system).start(
      typeName = akkaShardRegion,
      entityProps = KinesisConsumerSlave.props(snapshotAfterMessageNr, processorActor).withDispatcher("kinesis-consumer-slave-dispatcher"),
      settings = ClusterShardingSettings(system),
      extractEntityId = KinesisConsumerSlave.extractEntityId,
      extractShardId = KinesisConsumerSlave.extractShardId(akkaShardsNr)
    )

    // Create clustered slaves
    for (shard <- shards) {
      slaveCluster ! KinesisConsumerBootstrapCmd(awsRegion, streamName, shard.getShardId)
    }
  }

  override def receive: Receive = {
    case x: ShardRegion.GetClusterShardingStats =>
      val future = slaveCluster ? x
      pipe(future).to(sender())
  }
}

object KinesisConsumerSlave {
  val loopCmd = KinesisConsumerLoopCmd()
  val timeDiffGauge = Kamon.gauge("kinesis-read-delay", MeasurementUnit.time.milliseconds)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg@KinesisConsumerBootstrapCmd(_, _, entityId) => (entityId, msg)
  }
  val extractShardId: Int => ShardRegion.ExtractShardId = (numberOfShards) => {
    case KinesisConsumerBootstrapCmd(_, _, entityId) => (entityId.hashCode % numberOfShards).toString
    case ShardRegion.StartEntity(entityId) => (entityId.hashCode % numberOfShards).toString
  }

  def props[T](snapshotAfterMessageNr: Long, processorActor: ActorRef): Props =
    Props(new KinesisConsumerSlave(snapshotAfterMessageNr, processorActor))
}

class KinesisConsumerSlave[T](snapshotAfterMessageNr: Long, processorActor: ActorRef) extends PersistentActor with ActorLogging {

  import KinesisConsumerSlave._
  import context._

  val kinesisShardId: String = self.path.name

  override def persistenceId: String = kinesisShardId

  var state: KinesisConsumerActorState = _

  var client: AmazonKinesis = _

  var kinesisShardIterator: String = _

  var count: Long = 0

  override def receiveRecover: Receive = {
    // Will always be called, even if no state was persisted
    case RecoveryCompleted => {
      if (state != null) {
        createClient()
        createKinesisShardIterator()
        system.scheduler.scheduleOnce(1 second, self, loopCmd)
      }
    }
    case event: KinesisConsumerEvt => updateState(event)
    case SnapshotOffer(_, snapshot) => state = snapshot.asInstanceOf[KinesisConsumerActorState]
  }

  override def receiveCommand: Receive = {
    case KinesisConsumerBootstrapCmd(awsRegion, kinesisStreamName, _) =>
      if (state == null) {
        persistAsync(KinesisConsumerBootstrapEvt(awsRegion, kinesisStreamName)) { event =>
          updateState(event)
          createClient()
          createKinesisShardIterator()
          system.scheduler.scheduleOnce(1 second, self, loopCmd)
        }
      }
    case _: KinesisConsumerLoopCmd => {
      val getRecordsRequest = new GetRecordsRequest()
        .withShardIterator(kinesisShardIterator)
      val result = client.getRecords(getRecordsRequest)
      kinesisShardIterator = result.getNextShardIterator
      if (!result.getRecords.isEmpty) {
        val first = result.getRecords.get(0)
        // Record how far apart between the time of the first message in batch and now.  Increasing value means the consumer is slower than producer.
        val diff = System.currentTimeMillis() - first.getApproximateArrivalTimestamp.getTime
        timeDiffGauge.set(diff)
        persistAsync(KinesisConsumerUpdateSeqNrEvt(
          kinesisSeqNr = first.getSequenceNumber)
        )(updateState)
      }
      for (record <- result.getRecords.asScala) {
        val message = KinesisMessage.parseFrom(record.getData.array()).copy(kinesisSeqNr = record.getSequenceNumber)
        processorActor ! message
        count += 1
        if (count % snapshotAfterMessageNr == 0) {
          saveSnapshot(state)
        }
      }
      // Continue the polling
      context.system.scheduler.scheduleOnce(1 second, self, loopCmd)
    }
  }

  def updateState(evt: KinesisConsumerEvt): Unit = evt match {
    case KinesisConsumerBootstrapEvt(awsRegion, kinesisStreamName) =>
      if (state == null) {
        state = KinesisConsumerActorState(
          awsRegion,
          kinesisStreamName,
          kinesisSeqNr = null
        )
      } else {
        log.info("Ignore BootstrapMessage")
      }
    case KinesisConsumerUpdateSeqNrEvt(newSeqNr) => state = state.copy(kinesisSeqNr = newSeqNr)
  }

  def createClient(): Unit = {
    client = AmazonKinesisClient.builder()
      .withRegion(state.awsRegion)
      .build()
  }

  def createKinesisShardIterator(): Unit = {
    var getShardIteratorRequest = new GetShardIteratorRequest()
      .withStreamName(state.kinesisStreamName)
      .withShardId(kinesisShardId)

    if (StringUtils.isEmpty(state.kinesisSeqNr)) {
      getShardIteratorRequest = getShardIteratorRequest.withShardIteratorType("TRIM_HORIZON")
    } else {
      getShardIteratorRequest = getShardIteratorRequest
        .withShardIteratorType("AT_SEQUENCE_NUMBER")
        .withStartingSequenceNumber(state.kinesisSeqNr)
    }
    val result = client.getShardIterator(getShardIteratorRequest)
    kinesisShardIterator = result.getShardIterator
  }
}