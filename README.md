# The YaCy Searchlab

We are creating a YaCy portal which can be used to crawl the web and evaluate
everyting that we find in multiple ways. It will be a Search-as-a-Service
portal that is hosted online but can also be downloaded by everyone.

## This is work in progress

What you can find here is the early stage of development.

The searchlab will make use of the existing YaCy Grid search engine technology.
The public search portal will provide data science dashboards and user accounts.
All elements are free software and hosted in this repository or other repositories
of the github.com/yacy organization.

To follow the implementation process, have a look at the milestones M1-M6 within
the https://github.com/yacy/searchlab/issues issues.

To read more details about the project, visit https://wip.searchlab.eu/about/

## Searchlab and YaCy Grid Architecture

The searchlab application (this repository) was made as the front-end for the
YaCy Grid ecosystem. It uses mainly the following components:
- YaCy Searchlab - this project
- YaCy Grid Crawler https://github.com/yacy/yacy_grid_crawler
- YaCy Grid Loader https://github.com/yacy/yacy_grid_loader
- YaCy Grid Parser https://github.com/yacy/yacy_grid_parser
- a S3 store, we are using minio from https://min.io/ but you will be able to use any other implementation
- an elasticsearch instance, we will use opensearch from https://opensearch.org/

## The Searchlab Application Server

A careful selection of the correct web design, an appropriate application server
and overall web technology for a typical full-stack application had to be made.
We refrained from complex one-page node-based front-end application schema and
created instead a more classical design using server-rendered web pages and a
static-code generator together with modern data-driven concepts and API designes.

- the web front-end is created using the static code generator [MKDocs](https://www.mkdocs.org). Its source path is `ui`.
- the template for the web front-end is based on [MkDocs-Theme-Cinder-Superhero](https://github.com/Orbiter/MkDocs-Theme-Cinder-Superhero) which is a
privacy-aware combination of the [Cinder](https://sourcefoundry.org/cinder/) Bootstrap-Theme for MKdocs with a theme adoption to turn it into
a dark-mode version of Cinder using design ideas of [Superhero](https://bootswatch.com/superhero/).
- the back-end server is written in java and uses [Undertow](https://undertow.io/) as web server.
- content within the mkdocs can use the [handlebars](https://handlebarsjs.com/) template engine for dynamic/server-side content management. This feature has two elements:
  * a handlebars template engine integration in the undertow server usage
  * an api concept where each web page that uses undertow requires a json API which provides data for the undertow template. Even if the undertow template process also runs server-side, the API for the content that is handles must be provided as an external API.
- [server-side includes](https://en.wikipedia.org/wiki/Server_Side_Includes) allow the integration of server-rendered add-on content. This can be used to inject tablesaw- or plotly-generated html (see below). 
- as a server-internal data structure, [tablesaw](https://jtablesaw.github.io/tablesaw/gettingstarted) provides data table libraries for data science functions. This library allows the ouput of plotly-based time-series data (see below).
- [plotly](https://plotly.com/python/) graphs to visualize tables as graphs are added by tablesaw
- to further provide an excel-like experience to users who require this approach [Bootstrap Table](https://bootstrap-table.com/) is used for extended table visualization. This contains a large variety of search end export function.
- To visualize workflows, we integrated also [Mermaid](https://mermaid-js.github.io/mermaid/#/) for diagrams using text and code inside the MKDocs code.

## Source Code Release

The source code is released simply by providing a git clone opportunity using this github account.
To get the source code, just run
```
git clone https://github.com/yacy/searchlab.git
```

If you just want to download a zip file with all source,
use this link: https://github.com/yacy/searchlab/archive/refs/heads/master.zip

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

## Docker Release

A docker release can be produced in one simple step: just run
```
docker build -t searchlab .
```

... and a docker image will be in your local docker image store which can be started with
```
docker run -d --rm -p 8400:8400 --name searchlab searchlab
```

We publish docker images of the searchlab application also at dockerhub which can be obtained simply with

```
docker run -d --rm -p 8400:8400 --name searchlab yacy/searchlab
```

