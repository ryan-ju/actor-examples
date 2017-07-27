#!/bin/bash
echo "==== Create /tmp/init ===="
mkdir -p /tmp/init
echo "==== Getting public IP ====" >> /tmp/init/init.log 2>&1
PUBLIC_IP=$(curl http://169.254.169.254/latest/meta-data/public-ipv4) >> /tmp/init/init.log 2>&1
echo "==== PUBLIC_IP is $PUBLIC_IP ====" >> /tmp/init/init.log 2>&1
echo "==== Disable bnconfig ====" >> /tmp/init/init.log 2>&1
mv /opt/bitnami/cassandra/bnconfig /opt/bitnami/cassandra/bnconfig.disabled >> /tmp/init/init.log 2>&1
echo "==== Copy cassandra.yaml ====" >> /tmp/init/init.log 2>&1
cp ./cassandra.yaml /opt/bitnami/cassandra/conf/cassandra.yaml >> /tmp/init/init.log 2>&1
echo "==== Replace parameters in cassandra.yaml ====" >> /tmp/init/init.log 2>&1
sed -i -e s/{SEED_NODES}/$SEED_NODES/g /opt/bitnami/cassandra/conf/cassandra.yaml
echo "==== Copy cassandra-env.sh ====" >> /tmp/init/init.log 2>&1
cp ./cassandra-env.sh /opt/bitnami/cassandra/conf/ >> /tmp/init/init.log 2>&1
echo "==== Copy jvm.options ====" >> /tmp/init/init.log 2>&1
cp ./jvm.options /opt/bitnami/cassandra/conf/jvm.options >> /tmp/init/init.log 2>&1
echo "==== Replace parameters in jvm.options ====" >> /tmp/init/init.log 2>&1
sed -ir s/{PUBLIC_IP}/$PUBLIC_IP/g /opt/bitnami/cassandra/conf/jvm.options >> /tmp/init/init.log 2>&1
echo "==== Create /var/log/cassandra ====" >> /tmp/init/init.log 2>&1
mkdir -p /var/log/cassandra
chown -R bitnami:bitnami /var/log/cassandra
chmod a+w /var/log/cassandra
echo "==== Stop Cassandra ====" >> /tmp/init/init.log 2>&1
/opt/bitnami/ctlscript.sh stop >> /tmp/init/init.log 2>&1
echo "==== Remove default data ====" >> /tmp/init/init.log 2>&1
rm -rf /opt/bitnami/cassandra/data/*
echo "==== Start Cassandra ====" >> /tmp/init/init.log 2>&1
/opt/bitnami/ctlscript.sh start >> /tmp/init/init.log 2>&1
echo "==== Cassandra started ====" >> /tmp/init/init.log 2>&1
