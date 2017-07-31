#!/bin/bash

USAGE="Usage: upload-config.sh grantee bucket"

USER=$1
BUCKET=$2
if [[ -z $USER ]]; then
  echo "Must set grantee username"
  echo "$USAGE"
  exit 1
fi 

if [[ -z $BUCKET ]]; then
  echo "Must set S3 bucket"
  echo "$USAGE"
  exit 1
fi

if [[ ! -d ./cassandra-config ]]; then
  echo "Must have ./cassandra-config dir"
  echo "$USAGE"
  exit 1
fi

if [[ ! -d ./service-config ]]; then
  echo "Must have ./service-config dir"
  echo "$USAGE"
  exit 1
fi

if [[ ! -d ./injector-config ]]; then
  echo "Must have ./injector-config dir"
  echo "$USAGE"
  exit 1
fi

tar -czvf cassandra-config.tar.gz ./cassandra-config/
aws s3 cp ./cassandra-config.tar.gz "s3://$BUCKET/cassandra-config.tar.gz"

tar -czvf service-config.tar.gz ./service-config/
aws s3 cp ./service-config.tar.gz "s3://$BUCKET/service-config.tar.gz"

tar -czvf injector-config.tar.gz ./injector-config/
aws s3 cp ./injector-config.tar.gz "s3://$BUCKET/injector-config.tar.gz"
