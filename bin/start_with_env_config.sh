#!/usr/bin/env sh
cd "`dirname $0`"

### use this file as template to configure your own start script ###

export SEARCHLAB_PORT="8400" && \
export SEARCHLAB_GRID_S3_ADDRESS="admin:12345678@searchlab.127.0.0.1:9000" && \
export SEARCHLAB_GRID_ELASTICSEARCH_ADDRESS="127.0.0.1:9300" && \
export SEARCHLAB_GRID_BROKER_ADDRESS="guest:guest@127.0.0.1:5672" && \
./start.sh
