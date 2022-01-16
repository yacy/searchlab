## Search

<form class="input-group" name="searchform" action="/apps/search/">
<input type="text" value="{{channels.[0].searchTerms}}" name="query" id="query" class="form-control" size="80" maxlength="100" autofocus="autofocus" onFocus="this.select()" onClick="document.getElementById('startRecord').value=0;document.getElementById('query').value='';"/>
<input type="hidden" name="startRecord" id="startRecord" value="0"/>
<span class="input-group-btn">
<button id="search" type="submit" class="btn btn-default">search</button>
</span>
</form>

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
  <button type="button" class="btn btn-{{#if this.same}}success{{else}}default{{/if}} btn-xs" onClick="document.getElementById('startRecord').value={{this.startRecord}}; document.searchform.submit();">{{{this.page}}}</button>
{{/each}}
</div>
