#!/bin/bash
ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"
$ROOT/build/distributions/courier-realtime-kinesis-injector/bin/courier-realtime-kinesis-injector "$@"

