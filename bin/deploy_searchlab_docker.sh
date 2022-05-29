#!/bin/bash
cd "`dirname $0`"

# the difference between start_searchlab_docker and deploy_searchlab_docker is:
# - two redundant instances are running, one on port 8400 and another on port 8401
# - the docker image is always generated from source independent on the existance of one
# - the generated image must be new and gets a time-stamped tag
# - no external image is used
# - the containers run in production mode always
# - alternative ports can be used for test instances (8402 and 8403)

port0="8400"
port1="8401"
bindhost="127.0.0.1"
callhost=localhost
appname="Searchlab"
containername0=searchlab0
containername1=searchlab1
imagename="searchlab:ci`date +"%Y%m%d%H%M"`"
#imagename="searchlab:ci202205261053"

usage() { echo "usage: $0 [-t | --test]" 1>&2; exit 1; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        -t | --test ) port0="8402"; port1="8403"; containername0="searchlab2"; containername1="searchlab3"; shift 1;;
        -h | --help | -* | --* | * ) usage;;
    esac
done

# audit
function dockerps {
    docker ps -a --format "table {{.ID}}\t{{.Image}}\t{{.Names}}\t{{.Mounts}}\t{{.Status}}\t{{.Ports}}"
}

# build fresh image (always)
function build {
    cd ../
    git pull origin master
    cd ../searchlab_apps
    git pull origin master
    cd ..
    docker build -t ${imagename} -f searchlab/Dockerfile .
    cd searchlab/bin
}

# one function for two separate deployments
function deploy {
    number=$1          # $1 number of the instance: 0 for first one, 1 for second one :)
    port=$2            # $2 port number
    containername=$3   # $3 the name of the container

    # stop and remove container
    containerRuns=$(docker ps | grep -i "${containername}" | wc -l ) 
    containerExists=$(docker ps -a | grep -i "${containername}" | wc -l ) 
    if [ ${containerRuns} -gt 0 ]; then
        echo "${appname} container ${number} is already running, stopping.."
        docker stop ${containername}
    fi
    if [ ${containerExists} -gt 0 ]; then
        echo "${appname} container ${number} exists, removing.."
        docker rm ${containername}
    fi

    # start container 0
    docker run -d --restart=unless-stopped -p ${bindhost}:${port}:8400 \
           --link yacy-grid-minio --link yacy-grid-rabbitmq --link yacy-grid-elasticsearch \
           -e SEARCHLAB_GRID_S3_ADDRESS=admin:12345678@yacygrid.yacy-grid-minio:9000 \
           -e SEARCHLAB_GRID_BROKER_ADDRESS=guest:guest@yacy-grid-rabbitmq:5672 \
           -e SEARCHLAB_GRID_ELASTICSEARCH_ADDRESS=yacy-grid-elasticsearch:9300 \
           --name ${containername} ${imagename}
    echo "${appname} container ${number} started."
}

function wait4port {
    port=$1
    containername=$2
    loop=0
    while [ $loop -lt 10 ]; do
	sleep 2
	ready=$(curl --retry 10 --retry-connrefused --retry-delay 1 --write-out '%{http_code}' --silent --output /dev/null http://${callhost}:${port}/en/api/ready.json || true)
	if [ $ready -eq 200 ]; then return; fi
	docker logs --tail 20 $containername
	echo "service at port $port is not ready, waiting..."
	let loop=loop+1
    done
    exit -1
}

# CI process: build - deploy
build
dockerps | grep searchlab
deploy 0 $port0 $containername0
wait4port $port0 $containername0
dockerps | grep searchlab
deploy 1 $port1 $containername1
wait4port $port1 $containername1
dockerps | grep searchlab

# clean up
docker images --filter "dangling=true" -q --no-trunc | xargs docker rmi || true

# success
echo "Searchlab deployed from image $imagename at port $port0 and $port1"
