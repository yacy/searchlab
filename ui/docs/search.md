disable_toc: true

## Search

<form class="input-group input-group-lg" name="searchform" action=".">
<input type="text" value="{{channels.[0].searchTerms}}" name="q" id="query" class="form-control" size="80" maxlength="100" autofocus="autofocus" onFocus="this.select()" onClick="document.getElementById('start').value=0;document.getElementById('query').value='';"/>
<input type="hidden" name="start" id="start" value="0"/>
<span class="input-group-btn">
<button id="search" type="submit" class="btn btn-default" onClick="if (document.getElementById('start').value == '0') {document.getElementById('start').remove();}">search</button>
</span>
</form>

{{#if channels.[0]}}
 
  <div class="admonition note">
    <p>Other search interfaces from the <a href="../apps/data_studio/">data studio:</a></p>
    <p>
      <a href="../app/facetpiechart/"><img src="../app/facetpiechart/screenshot.png" width="128" height="128"></a>
      <a href="../app/websearch_bootstrap/"><img src="../app/websearch_bootstrap/screenshot.png" width="128" height="128"></a>
      <a href="../app/websearch_lit/"><img src="../app/websearch_lit/screenshot.png" width="128" height="128"></a>
      <a href="../app/websearch_yaml4/"><img src="../app/websearch_yaml4/screenshot.png" width="128" height="128"></a>
    </p>
  </div>
  
  {{#if channels.[0].items}}
    <p>{{channels.[0].totalResults}} hits, page {{channels.[0].page}} of {{channels.[0].pages}}</p>
    {{#each channels.[0].items}}
      <div class="panel panel-default">
        <div class="panel-heading">
          <h3 class="panel-title"><a href="{{this.link}}" target="_blank">{{this.title}}</a></h3>
        </div>
        <div class="panel-body">
          {{this.description}}
        </div>
        <div class="panel-footer">
          <a href="{{this.link}}" target="_blank">{{this.link}}</a><br>{{this.sizename}}
        </div>
      </div>
    {{/each}}
    <div class="btn-group" role="group" aria-label="pagination">
    {{#each channels.[0].pagenav}}
      <button type="button" class="btn btn-{{#if this.same}}success{{else}}default{{/if}} btn-xs" onClick="document.getElementById('start').value={{this.startRecord}}; document.searchform.submit();">{{{this.page}}}</button>
    {{/each}}
    </div>
  {{else}}
    <p>no results</p>
  {{/if}}
{{/if}}
