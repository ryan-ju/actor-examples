#!/bin/bash
echo "==== Configurations ====" >> /tmp/init/init.log 2>&1
echo "SEED_NODES=$SEED_NODES" >> /tmp/init/init.log 2>&1
echo "BUCKET=$BUCKET" >> /tmp/init/init.log 2>&1
echo "KINESIS=$KINESIS" >> /tmp/init/init.log 2>&1
echo "CASSANDRA_SEEDS=$CASSANDRA_SEEDS" >> /tmp/init/init.log 2>&1
echo "CASSANDRA_REPLICATION_FACTOR=$CASSANDRA_REPLICATION_FACTOR" >> /tmp/init/init.log 2>&1
echo "GRID_SIZE=$GRID_SIZE" >> /tmp/init/init.log 2>&1
echo "SHARD_NR=$SHARD_NR" >> /tmp/init/init.log 2>&1
echo "SNAPSHOT_AFTER=$SNAPSHOT_AFTER" >> /tmp/init/init.log 2>&1
echo "OFFLINE_AFTER_S=$OFFLINE_AFTER_S" >> /tmp/init/init.log 2>&1
echo "WS=$WS" >> /tmp/init/init.log 2>&1

echo "==== Create /tmp/init ===="
mkdir -p /tmp/init >> /tmp/init/init.log 2>&1
echo "==== Set net.ipv4.ip_local_port_range to 32768 65535 ====" >> /tmp/init/init.log 2>&1
echo "net.ipv4.ip_local_port_range = 32768 65535" > /etc/sysctl.conf 
sysctl -p /etc/sysctl.conf >> /tmp/init/init.log 2>&1
echo "==== System tuning ====" >> /tmp/init/init.log 2>&1
sysctl net.core.rmem_max=2097152 >> /tmp/init/init.log 2>&1
sysctl net.core.wmem_max=2097152 >> /tmp/init/init.log 2>&1
echo "net.core.rmem_max=$(cat /proc/sys/net/core/rmem_max)" >> /tmp/init/init.log 2>&1
echo "net.core.wmem_max=$(cat /proc/sys/net/core/wmem_max)" >> /tmp/init/init.log 2>&1
echo "==== Install sysstat ====" >> /tmp/init/init.log 2>&1
yum install -y sysstat >> /tmp/init/init.log 2>&1
echo "==== Install nethogs ====" >> /tmp/init/init.log 2>&1
yum install -y nethogs >> /tmp/init/init.log 2>&1
echo "==== Install iftop ====" >> /tmp/init/init.log 2>&1
yum install -y iftop >> /tmp/init/init.log 2>&1
echo "==== Install iptraf ====" >> /tmp/init/init.log 2>&1
yum install -y iptraf >> /tmp/init/init.log 2>&1
echo "==== Install dstat ====" >> /tmp/init/init.log 2>&1
yum install -y dstat >> /tmp/init/init.log 2>&1
echo "==== Install Datadog agent ====" >> /tmp/init/init.log 2>&1
DD_API_KEY="$DATADOG_API_KEY" bash -c "$(curl -L https://raw.githubusercontent.com/DataDog/dd-agent/master/packaging/datadog-agent/source/install_agent.sh)" >> /tmp/init/init.log 2>&1
echo "==== Configure Datadog agent ====" >> /tmp/init/init.log 2>&1
sed -i -e '/# histogram_percentiles/a\
histogram_percentiles: 0.95, 0.99, 0.999' /etc/dd-agent/datadog.conf >> /tmp/init/init.log 2>&1
echo "==== Restart Datadog agent ====" >> /tmp/init/init.log 2>&1
/etc/init.d/datadog-agent restart >> /tmp/init/init.log 2>&1
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
JAVA_OPTS="$JAVA_OPTS -Xms2G -Xmx2G"
JAVA_OPTS="$JAVA_OPTS -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled"
JAVA_OPTS="$JAVA_OPTS -XX:CMSInitiatingOccupancyFraction=70 -XX:+UseCMSInitiatingOccupancyOnly"
JAVA_OPTS="$JAVA_OPTS -XX:CMSWaitDuration=5000"
JAVA_OPTS="$JAVA_OPTS -XX:+CMSClassUnloadingEnabled"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintHeapAtGC -XX:+PrintTenuringDistribution -XX:+PrintGCApplicationStoppedTime"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintPromotionFailure -XX:PrintFLSStatistics=1 -Xloggc:/var/courier-realtime/logs/gc.log -XX:+UseGCLogFileRotation"
JAVA_OPTS="$JAVA_OPTS -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=10M"
# JMX
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.ssl=false"
JAVA_OPTS="$JAVA_OPTS -Djava.rmi.server.hostname=$PUBLIC_IP"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=7199"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.rmi.port=7199"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=false"
# Application
index=0
for seed in ${SEED_NODES//,/ }; do
  JAVA_OPTS="$JAVA_OPTS -Dakka.cluster.seed-nodes.$index=akka://cluster-system@$seed:2550"
#  JAVA_OPTS="$JAVA_OPTS -Dakka.cluster.seed-nodes.$index=akka.tcp://cluster-system@$seed:2550"
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
# JAVA_OPTS="$JAVA_OPTS -Dakka.remote.netty.tcp.hostname=$PRIVATE_IP -Dakka.remote.netty.tcp.port=2550"
JAVA_OPTS="$JAVA_OPTS -Dapplication.gridSize=$GRID_SIZE"
JAVA_OPTS="$JAVA_OPTS -Dapplication.kinesis-consumer-cluster.streamName=$KINESIS"
JAVA_OPTS="$JAVA_OPTS -Dapplication.kinesis-consumer-cluster.shardNr=$SHARD_NR"
JAVA_OPTS="$JAVA_OPTS -Dapplication.kinesis-consumer-cluster.snapshotAfterMessageNr=$SNAPSHOT_AFTER"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier-cluster.shardNr=$SHARD_NR"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier-cluster.snapshotAfterMessageNr=$SNAPSHOT_AFTER"
JAVA_OPTS="$JAVA_OPTS -Dapplication.grid-cluster.shardNr=$SHARD_NR"
JAVA_OPTS="$JAVA_OPTS -Dapplication.grid-cluster.snapshotAfterMessageNr=$SNAPSHOT_AFTER"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier.offlineAfterS=$OFFLINE_AFTER_S"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier.publish=$WS"
JAVA_OPTS="$JAVA_OPTS -Dapplication.websocket.port=8080"
JAVA_OPTS="$JAVA_OPTS -Dcassandra-journal.replication-factor=$CASSANDRA_REPLICATION_FACTOR"
JAVA_OPTS="$JAVA_OPTS -Dcassandra-snapshot-store.replication-factor=$CASSANDRA_REPLICATION_FACTOR"
JAVA_OPTS="$JAVA_OPTS -Dlogback.output.dir=/var/courier-realtime/logs"
# Run application in background
echo "==== JAVA_OPTS=$JAVA_OPTS ====" >> /tmp/init/init.log 2>&1
echo "==== Application starting ... ====" >> /tmp/init/init.log 2>&1
JAVA_OPTS="$JAVA_OPTS" nohup $ROOT/bin/courier-realtime-service &
echo "==== Application started ====" >> /tmp/init/init.log 2>&1