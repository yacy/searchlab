# Searchlab Architecture & Onboarding

This is a guide for developers of this platform. It explains frameworks and libraries for this application
together with sample use cases. 


## Application Server

For this application server we combine [MKDocs](https://www.mkdocs.org) for static web pages with [Undertow](https://undertow.io/) as web server.
As a design basis for MKdocs we use [MkDocs-Theme-Cinder-Superhero](https://github.com/Orbiter/MkDocs-Theme-Cinder-Superhero) which is a
privacy-aware combination of the [Cinder](https://sourcefoundry.org/cinder/) Bootstrap-Theme for MKdocs with a theme adoption to turn it into
a dark-mode version of Cinder using design ideas of [Superhero](https://bootswatch.com/superhero/). The theme is now privacy-aware because it
does not use any external resources (like external/linked libraries and/or fonts) and embedds all those elements which nowadays are linked through
a CDN.

<table>
<tr>
<td style="background: transparent; border: none;"><img src="../../img/tile_MkDocs.png" width="224"><br/>MkDocs</td>
<td style="background: transparent; border: none;"><img src="../../img/tile_Cinder.png" width="224"><br/>Cinder</td>
<td style="background: transparent; border: none;"><img src="../../img/tile_Superhero.png" width="224"><br/>Superhero</td>
<td style="background: transparent; border: none;"><img src="../../img/tile_Undertow.png" width="224"><br/>Undertow</td>
</tr>

</table>

The following elements are added to the undertow server:

- [handlebars](https://handlebarsjs.com/) template engine for dynamic/server-side content management
- [tablesaw](https://jtablesaw.github.io/tablesaw/gettingstarted) data table libraries for data science functions
- [plotly](https://plotly.com/python/) graphs to visualize tables as graphs are added by tablesaw
- [Bootstrap Table](https://bootstrap-table.com/) for extended table visualization
- [Mermaid](https://mermaid-js.github.io/mermaid/#/) for diagrams using text and code inside the MKDown.


## Storage

We will use two external storage systems: a S3 Bucket Store and an Elasticsearch search index. Both storage systems can be hosted with free software and we will use min.io for S3 storage and opensearch.org for Elasticsearch storage. You may choose other (also: cloud-hosted) services if you host the Searchlab yourself.


## <span class="glyphicon glyphicon-info-sign" aria-hidden="true"></span> This is Work-In-Progress

This is a placeholder page that will migrate into the actual application as soon as it is finished.
What we want to implemted is described in the [milestones M1-M6](https://github.com/yacy/searchlab/issues).

<span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span> Follow messages from YaCy maitainer [@orbiterlab](https://twitter.com/orbiterlab) and the project updates from [@yacy_search](https://twitter.com/yacy_search) to get news about the project milestones. Please share and <span class="glyphicon glyphicon-heart" aria-hidden="true"></span> like to help building this page!

<div class="alert alert-warning" role="alert">everything below is work-in-progress</div>


## Servlets and Document Types


## Building the application


## Set-up a development environment