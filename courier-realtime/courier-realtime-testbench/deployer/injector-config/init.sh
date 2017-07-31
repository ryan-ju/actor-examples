#!/bin/bash
echo "==== Configurations ====" >> /tmp/init/init.log 2>&1
echo "BUCKET=$BUCKET" >> /tmp/init/init.log 2>&1
echo "KINESIS=$KINESIS" >> /tmp/init/init.log 2>&1
# Number of messages per Kinesis send record request.  When a batch reaches this size it will be sent immediately.
echo "BATCH_SIZE=$BATCH_SIZE" >> /tmp/init/init.log 2>&1
# The delay between batches
echo "BATCH_TIMEOUT_MS=$BATCH_TIMEOUT_MS" >> /tmp/init/init.log 2>&1
echo "COURIER_NR=$COURIER_NR" >> /tmp/init/init.log 2>&1
echo "COURIER_PING_MS=$COURIER_PING_MS" >> /tmp/init/init.log 2>&1
echo "COURIER_STEP_SIZE=$COURIER_STEP_SIZE" >> /tmp/init/init.log 2>&1
echo "GRID_SIZE=$GRID_SIZE" >> /tmp/init/init.log 2>&1

echo "==== Create /tmp/init ===="
mkdir -p /tmp/init >> /tmp/init/init.log 2>&1
echo "==== System tuning ====" >> /tmp/init/init.log 2>&1
sysctl net.core.rmem_max=2097152 >> /tmp/init/init.log 2>&1
sysctl net.core.wmem_max=2097152 >> /tmp/init/init.log 2>&1
echo "net.core.rmem_max=$(cat /proc/sys/net/core/rmem_max)" >> /tmp/init/init.log 2>&1
echo "net.core.wmem_max=$(cat /proc/sys/net/core/wmem_max)" >> /tmp/init/init.log 2>&1
echo "==== Getting public IP ====" >> /tmp/init/init.log 2>&1
PUBLIC_IP=$(curl http://169.254.169.254/latest/meta-data/public-ipv4) >> /tmp/init/init.log 2>&1
echo "==== PUBLIC_IP is $PUBLIC_IP ====" >> /tmp/init/init.log 2>&1
echo "==== Create /var/kinesis-injector ====" >> /tmp/init/init.log 2>&1
mkdir -p /var/kinesis-injector/logs/archived >> /tmp/init/init.log 2>&1
chown -R centos:centos /var/kinesis-injector >> /tmp/init/init.log 2>&1
cd /var/kinesis-injector >> /tmp/init/init.log 2>&1
echo "==== Download service tar from S3 ====" >> /tmp/init/init.log 2>&1
aws s3 cp s3://$BUCKET/repo/courier-realtime-kinesis-injector-1.0.0-SNAPSHOT.tar ./ >> /tmp/init/init.log 2>&1
tar -xf courier-realtime-kinesis-injector-1.0.0-SNAPSHOT.tar >> /tmp/init/init.log 2>&1
mv ./courier-realtime-kinesis-injector-1.0.0-SNAPSHOT ./courier-realtime-kinesis-injector >> /tmp/init/init.log 2>&1
chown -R centos:centos ./courier-realtime-kinesis-injector >> /tmp/init/init.log 2>&1
ROOT="./courier-realtime-kinesis-injector"
# JVM
JAVA_OPTS="-javaagent:$ROOT/lib/aspectjweaver-1.8.10.jar $JAVA_OPTS"
JAVA_OPTS="$JAVA_OPTS -Xms2G -Xmx2G"
JAVA_OPTS="$JAVA_OPTS -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled"
JAVA_OPTS="$JAVA_OPTS -XX:CMSInitiatingOccupancyFraction=70 -XX:+UseCMSInitiatingOccupancyOnly"
JAVA_OPTS="$JAVA_OPTS -XX:CMSWaitDuration=5000"
JAVA_OPTS="$JAVA_OPTS -XX:+CMSClassUnloadingEnabled"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintHeapAtGC -XX:+PrintTenuringDistribution -XX:+PrintGCApplicationStoppedTime"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintPromotionFailure -XX:PrintFLSStatistics=1 -Xloggc:/var/kinesis-injector/logs/gc.log -XX:+UseGCLogFileRotation"
JAVA_OPTS="$JAVA_OPTS -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=10M"
# JMX
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.ssl=false"
JAVA_OPTS="$JAVA_OPTS -Djava.rmi.server.hostname=$PUBLIC_IP"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=7199"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.rmi.port=7199"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=false"
# Application
JAVA_OPTS="$JAVA_OPTS -Dapplication.kinesis.streamName=$KINESIS"
JAVA_OPTS="$JAVA_OPTS -Dapplication.kinesis.batchSize=$BATCH_SIZE"
JAVA_OPTS="$JAVA_OPTS -Dapplication.kinesis.timeoutMs=$BATCH_TIMEOUT_MS"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier.nr=$COURIER_NR"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier.pingPeriodMs=$COURIER_PING_MS"
JAVA_OPTS="$JAVA_OPTS -Dapplication.courier.step=$COURIER_STEP_SIZE"
JAVA_OPTS="$JAVA_OPTS -Dapplication.grid.width=$GRID_SIZE"
JAVA_OPTS="$JAVA_OPTS -Dapplication.grid.height=$GRID_SIZE"
JAVA_OPTS="$JAVA_OPTS -Dapplication.websocket.port=8080"
JAVA_OPTS="$JAVA_OPTS -Dlogback.output.dir=/var/kinesis-injector/logs"
# Run application in background
echo "==== JAVA_OPTS=$JAVA_OPTS ====" >> /tmp/init/init.log 2>&1
echo "==== Application starting ... ====" >> /tmp/init/init.log 2>&1
JAVA_OPTS="$JAVA_OPTS" nohup $ROOT/bin/courier-realtime-kinesis-injector &
echo "==== Application started ====" >> /tmp/init/init.log 2>&1
