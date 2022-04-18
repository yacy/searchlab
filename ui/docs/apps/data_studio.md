# Data Studio Apps

This is a collection of Search Widgets and Dashboards that can be used
with the Searchlab at https://searchlab.eu and in your own web pages.

Data Studio Apps serve two purposes:

- provide more than web search: analyze texts and search indexes, statistical evaluation and visualization, dashboards and data export
- an easy way to integrate those pages into your own application

If you ever asked "How can I integrate a search function in my web page" then read the integration section below.

## Search App Collection
Click on the tile to start the app:

{{#each .}}
<div class="card" style="width:280px; height:346px; border-radius: 8px; display: flex; flex-flow: column; background-color:#4E5D6C; margin-right:12px; margin-bottom:12px; padding:12px; position:relative; float:left; display:block; overflow: hidden;">
  <a href="../../app/{{this.path}}/" target="_blank" rel="noopener noreferrer" >
    <img src="../../app/{{this.path}}/screenshot.png" class="card-img-top" style="border-radius: 4px;" alt="{{this.name}}" width="256" height="256">
  </a>
  <div class="card-body" style="flex:auto;">
    <h5 class="card-title">{{this.name}}</h5>
    <p class="card-text">{{this.headline}}</p>
  </div>
</div>

{{/each}}
<div style="clear:both;"></div>


## Installation

These apps can be used independently from the Searchlab. They are hosted in it's own git repository at 
[https://github.com/yacy/searchlab_apps](https://github.com/yacy/searchlab_apps).

For integration of single apps inside your own web pages, just copy the
corresponding app inside your own content. Because of the CC0 license you don't
need to mention the source, just go ahead and use what you can find here.
You will probably need to integrate `css` and `js` code from the `htdocs/css`
and `htdocs/js` as well.
