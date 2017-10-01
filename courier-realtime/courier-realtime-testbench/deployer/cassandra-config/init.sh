#!/bin/bash
echo "==== Create /tmp/init ===="
mkdir -p /tmp/init
echo "==== Set net.ipv4.ip_local_port_range to 32768 65535 ====" >> /tmp/init/init.log 2>&1
echo "net.ipv4.ip_local_port_range = 32768 65535" > /etc/sysctl.conf 
sysctl -p /etc/sysctl.conf >> /tmp/init/init.log 2>&1
echo "==== Soft link java in /usr/bin ====" >> /tmp/init/init.log 2>&1
ln -s /opt/bitnami/java/bin/java /usr/bin/java >> /tmp/init/init.log 2>&1
echo "==== Getting public IP ====" >> /tmp/init/init.log 2>&1
export PUBLIC_IP=$(curl http://169.254.169.254/latest/meta-data/public-ipv4) >> /tmp/init/init.log 2>&1
echo "==== PUBLIC_IP is $PUBLIC_IP ====" >> /tmp/init/init.log 2>&1
echo "==== Get private IP ====" >> /tmp/init/init.log 2>&1
export PRIVATE_IP=$(curl http://169.254.169.254/latest/meta-data/local-ipv4) >> /tmp/init/init.log 2>&1
echo "==== PRIVATE is $PRIVATE_IP ====" >> /tmp/init/init.log 2>&1
echo "==== Disable bnconfig ====" >> /tmp/init/init.log 2>&1
mv /opt/bitnami/cassandra/bnconfig /opt/bitnami/cassandra/bnconfig.disabled >> /tmp/init/init.log 2>&1
if [[ ! -z "$DATADOG_API_KEY" ]]; then 
  echo "==== Install Datadog agent ====" >> /tmp/init/init.log 2>&1
  DD_API_KEY="$DATADOG_API_KEY" bash -c "$(curl -L https://raw.githubusercontent.com/DataDog/dd-agent/master/packaging/datadog-agent/source/install_agent.sh)" >> /tmp/init/init.log 2>&1
  echo "==== Configure Datadog agent ====" >> /tmp/init/init.log 2>&1
  sed -i -e '/# histogram_percentiles/a\
  histogram_percentiles: 0.95, 0.99, 0.999' /etc/dd-agent/datadog.conf >> /tmp/init/init.log 2>&1
  cp /etc/dd-agent/conf.d/cassandra.yaml.example /etc/dd-agent/conf.d/cassandra.yaml >> /tmp/init/init.log 2>&1
  echo "==== Enable Datadog Cassandra reporting ====" >> /tmp/init/init.log 2>&1
  chown dd-agent:dd-agent /etc/dd-agent/conf.d/cassandra.yaml >> /tmp/init/init.log 2>&1
  echo "==== Restart Datadog agent ====" >> /tmp/init/init.log 2>&1
  /etc/init.d/datadog-agent restart >> /tmp/init/init.log 2>&1
else
  echo "==== Skip Datadog installation because DATADOG_API_KEY is not set ====" >> /tmp/init/init.log 2>&1
fi
echo "==== Copy cassandra.yaml ====" >> /tmp/init/init.log 2>&1
cp ./cassandra.yaml /opt/bitnami/cassandra/conf/cassandra.yaml >> /tmp/init/init.log 2>&1
echo "==== Replace parameters in cassandra.yaml ====" >> /tmp/init/init.log 2>&1
sed -i -e s/{SEED_NODES}/$SEED_NODES/g -e s/{PRIVATE_IP}/$PRIVATE_IP/g /opt/bitnami/cassandra/conf/cassandra.yaml
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
