#!/bin/bash
ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"
JAVA_OPTS="-javaagent:$ROOT/build/distributions/courier-realtime-service/lib/aspectjweaver-1.8.10.jar $JAVA_OPTS"
$ROOT/build/distributions/courier-realtime-service/bin/courier-realtime-service "$@"
