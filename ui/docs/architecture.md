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
<td style="background: transparent; border: none;"><img src="../img/tile_MkDocs.png" width="224"><br/>MkDocs</td>
<td style="background: transparent; border: none;"><img src="../img/tile_Cinder.png" width="224"><br/>Cinder</td>
<td style="background: transparent; border: none;"><img src="../img/tile_Superhero.png" width="224"><br/>Superhero</td>
<td style="background: transparent; border: none;"><img src="../img/tile_Undertow.png" width="224"><br/>Undertow</td>
</tr>

</table>

The following elements are added to the undertow server:

- [handlebars](https://handlebarsjs.com/) template engine for dynamic/server-side content management
- [tablesaw](https://jtablesaw.github.io/tablesaw/gettingstarted) data table libraries for data science functions
- [plotly](https://plotly.com/python/) graphs to visualize tables as graphs are added by tablesaw
- [Bootstrap Table](https://bootstrap-table.com/) for extended table visualization
- [Mermaid](https://mermaid-js.github.io/mermaid/#/) for diagrams using text and code inside the MKDown.

  <div class="mermaid">
    graph LR
    subgraph static html generator
      direction TB
      A[MKDocs] -->|provides html to| B(tablesaw)
      G[bootstrap table] -->|integrated| A
    end
    subgraph server
      B --> C{Let me thinkss}
      C -->|One| D[Laptop]
      C -->|Two| E[iPhone]
      C -->|Three| F[Car]
    end
  </div>

## Storage


## Servlets and Document Types


## Building the application


## Set-up a development environment