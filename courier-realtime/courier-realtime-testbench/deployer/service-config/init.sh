#!/bin/bash
echo "==== Configurations ====" >> /tmp/init/init.log 2>&1
echo "DATADOG_API_KEY=$DATADOG_API_KEY" >> /tmp/init/init.log 2>&1
echo "SEED_NODES=$SEED_NODES" >> /tmp/init/init.log 2>&1
echo "BUCKET=$BUCKET" >> /tmp/init/init.log 2>&1
echo "KINESIS=$KINESIS" >> /tmp/init/init.log 2>&1
echo "CASSANDRA_SEEDS=$CASSANDRA_SEEDS" >> /tmp/init/init.log 2>&1
echo "GRID_SIZE=$GRID_SIZE" >> /tmp/init/init.log 2>&1
echo "SHARD_NR=$SHARD_NR" >> /tmp/init/init.log 2>&1
echo "SNAPSHOT_AFTER=$SNAPSHOT_AFTER" >> /tmp/init/init.log 2>&1
echo "OFFLINE_AFTER_S=$OFFLINE_AFTER_S" >> /tmp/init/init.log 2>&1

echo "==== Create /tmp/init ===="
mkdir -p /tmp/init >> /tmp/init/init.log 2>&1
echo "==== Install Datadog agent ====" >> /tmp/init/init.log 2>&1
DD_API_KEY="$DATADOG_API_KEY" bash -c "$(curl -L https://raw.githubusercontent.com/DataDog/dd-agent/master/packaging/datadog-agent/source/install_agent.sh)" >> /tmp/init/init.log 2>&1
echo "==== Get private IP ====" >> /tmp/init/init.log 2>&1
export PRIVATE_IP=$(curl http://169.254.169.254/latest/meta-data/local-ipv4) >> /tmp/init/init.log 2>&1
echo "==== PRIVATE is $PRIVATE_IP ====" >> /tmp/init/init.log 2>&1
echo "==== Create /var/courier-realtime ====" >> /tmp/init/init.log 2>&1
mkdir -p /var/courier-realtime/logs/archived >> /tmp/init/init.log 2>&1
chown -R centos:centos /var/courier-realtime >> /tmp/init/init.log 2>&1
cd /var/courier-realtime >> /tmp/init/init.log 2>&1
echo "==== Download service tar from S3 ====" >> /tmp/init/init.log 2>&1
aws s3 cp s3://$BUCKET/repo/courier-realtime-service-1.0.0-SNAPSHOT.tar ./ >> /tmp/init/init.log 2>&1
tar -xf courier-realtime-service-1.0.0-SNAPSHOT.tar >> /tmp/init/init.log 2>&1
mv ./courier-realtime-service-1.0.0-SNAPSHOT ./courier-realtime-service >> /tmp/init/init.log 2>&1
chown -R centos:centos ./courier-realtime-service >> /tmp/init/init.log 2>&1
ROOT="./courier-realtime-service"
JAVA_OPTS="-javaagent:$ROOT/lib/aspectjweaver-1.8.10.jar $JAVA_OPTS"
JAVA_OPTS="$JAVA_OPTS -Xms3G -Xmx3G"
JAVA_OPTS="$JAVA_OPTS -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled"
JAVA_OPTS="$JAVA_OPTS -XX:CMSInitiatingOccupancyFraction=70 -XX:+UseCMSInitiatingOccupancyOnly"
JAVA_OPTS="$JAVA_OPTS –XX:CMSWaitDuration=5000"
JAVA_OPTS="$JAVA_OPTS -XX:+CMSClassUnloadingEnabled"
index=0
for seed in ${SEED_NODES//,/ }; do
  JAVA_OPTS="$JAVA_OPTS -Dakka.cluster.seed-nodes.$index=akka://cluster-system@$seed:2550"
  index=$((index + 1))
done
index=0
for seed in ${CASSANDRA_SEEDS//,/ }; do
  JAVA_OPTS="$JAVA_OPTS -Dapplication.cassandra.cluster.$index=$seed"
  JAVA_OPTS="$JAVA_OPTS -Dcassandra-journal.contact-points.$index=$seed"
  JAVA_OPTS="$JAVA_OPTS -Dcassandra-snapshot-store.contact-points.$index=$seed"
  index=$((index + 1))
done
JAVA_OPTS="$JAVA_OPTS -Dakka.remote.artery.canonical.hostname=$PRIVATE_IP -Dakka.remote.artery.canonical.port=2550"
JAVA_OPTS="$JAVA_OPTS -Dapplication.gridSize=$GRID_SIZE"
JAVA_OPTS="$JAVA_OPTS -Dapplication.kinesis-consumer-cluster.streamName=$KINESIS"
JAVA_OPTS="$JAVA_OPTS -Dapplication.kinesis-consumer-cluster.shardNr=$SHARD_NR"
JAVA_OPTS="$JAVA_OPTS -Dapplication.kinesis-consumer-cluster.snapshotAfterMessageNr=$SNAPSHOT_AFTER"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier-cluster.shardNr=$SHARD_NR"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier-cluster.snapshotAfterMessageNr=$SNAPSHOT_AFTER"
JAVA_OPTS="$JAVA_OPTS -Dapplication.grid-cluster.shardNr=$SHARD_NR"
JAVA_OPTS="$JAVA_OPTS -Dapplication.grid-cluster.snapshotAfterMessageNr=$SNAPSHOT_AFTER"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier.offlineAfterS=$OFFLINE_AFTER_S"
JAVA_OPTS="$JAVA_OPTS -Dapplication.websocket.port=8080"
JAVA_OPTS="$JAVA_OPTS -Dlogback.output.dir=/var/courier-realtime/logs"
# Run application in background
echo "==== JAVA_OPTS=$JAVA_OPTS ====" >> /tmp/init/init.log 2>&1
echo "==== Application starting ... ====" >> /tmp/init/init.log 2>&1
nohup JAVA_OPTS="$JAVA_OPTS" $ROOT/bin/courier-realtime-service >/dev/null 2>&1 &
echo "==== Application started ====" >> /tmp/init/init.log 2>&1