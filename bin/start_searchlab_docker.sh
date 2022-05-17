#!/bin/bash
cd "`dirname $0`"

bindhost="0.0.0.0"
callhost=`hostname`
appname="Searchlab"
containername=searchlab
imagename=${containername}

usage() { echo "usage: $0 [-p | --production]" 1>&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    -p | --production ) bindhost="127.0.0.1"; callhost="localhost"; imagename="yacy/${imagename//-/_}:latest"; shift 1;;
    -h | --help | -* | --* | * ) usage;;
  esac
done

containerRuns=$(docker ps | grep -i "${containername}" | wc -l ) 
containerExists=$(docker ps -a | grep -i "${containername}" | wc -l ) 
if [ ${containerRuns} -gt 0 ]; then
  echo "${appname} container is already running"
elif [ ${containerExists} -gt 0 ]; then
  docker start ${containername}
  echo "${appname} container re-started"
else
  if [[ $imagename != "yacy/"*":latest" ]] && [[ "$(docker images -q ${imagename} 2> /dev/null)" == "" ]]; then
      cd ../..
      docker build -t ${containername} -f searchlab/Dockerfile .
      cd searchlab/bin
  fi
  docker run -d --restart=unless-stopped -p ${bindhost}:8400:8400 \
	 --link yacy-grid-minio --link yacy-grid-rabbitmq --link yacy-grid-elasticsearch \
	 -e SEARCHLAB_GRID_S3_ADDRESS=admin:12345678@yacygrid.yacy-grid-minio:9000 \
	 -e SEARCHLAB_GRID_BROKER_ADDRESS=guest:guest@yacy-grid-rabbitmq:5672 \
	 -e SEARCHLAB_GRID_ELASTICSEARCH_ADDRESS=yacy-grid-elasticsearch:9300 \
	 --name ${containername} ${imagename}
  echo "${appname} started."
fi
docker ps -a --format "table {{.ID}}\t{{.Image}}\t{{.Names}}\t{{.State}}\t{{.Mounts}}\t{{.Ports}}"

echo "Open searchlab portal at http://${callhost}:8400/"


