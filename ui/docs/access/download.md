# Download and Install

Searchlab is licensed under [LGPL 2.1](https://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License) - you can use and integrate the searchlab into your own software.

## Download

The preferred way to obtain the software is using git. The source code is released simply by providing a git clone opportunity using this github account.
To get the source code, just run
```
git clone https://github.com/yacy/searchlab.git
```

If you just want to download a zip file with all source,
use this link: [https://github.com/yacy/searchlab/archive/refs/heads/master.zip](https://github.com/yacy/searchlab/archive/refs/heads/master.zip)

## Building the Application

We provide two methods to build-and-run the application: within a development enviromnment and with docker for production.

### Development Environment Build

To build the searchlab, you need the following components:

- python 3 and mkdocs which can simply be installed with `pip install mkdocs`
- java 8 (or higher) which can be obtained i.e. from https://adoptium.net/

The application is build in two steps:

- first, the static web pages must be created:
```
cd ui
mkdocs build
```

- second, the server must be compiled
```
./gradlew assemble
```

- finally, the application can be started with
```
./gradlew run
```

The searchlab application can then be accessed at http://localhost:8400/

### Production Environment Build

A docker release can be produced in one simple step: just run
```
docker build -t searchlab .
```

... and a docker image will be in your local docker image store which can be started with
```
docker run -d --rm -p 8400:8400 --name searchlab searchlab
```

Then the searchlab application can be accessed at http://localhost:8400/

## Running the Application

If you don't want to set-up a development environment you can just run the docker images that we provide with dockerhub releases:

```
docker run -d --rm -p 8400:8400 --name searchlab yacy/searchlab
```
