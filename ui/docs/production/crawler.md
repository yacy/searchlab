# Crawl Start


Start a Web crawl:

<form action=".">

<div class="form-group">
    <label for="crawlingURL">Start-URL</label>
    <input class="form-control" name="crawlingURL" id="crawlingURL" type="text" size="50" maxlength="256" value="" placeholder="https://..."/>
    <p class="help-block">The start-URL is a link on crawl depth 0</p>
</div>

<div class="form-group">
    <label for="crawlingDepth">Crawl Depth</label>
    <input class="form-control" name="crawlingDepth" id="crawlingDepth" type="text" size="2" maxlength="2" value="2"/>
    <p class="help-block">Depth 1 means: all linked documents from the start url are included.</p>
</div>

<div class="form-group">
    <label for="collection">Collection</label>
    <input class="form-control" name="collection" id="collection" type="text" size="50" maxlength="256" value="user"/>
    <p class="help-block">The start-URL is a link on crawl depth 0</p>
</div>

<div class="checkbox">
  <label>
    <input type="checkbox" name="priority" disabled="true">Priority
  </label>
</div>

<div class="checkbox">
  <label>
    <input type="checkbox" name="loaderHeadless" disabled>Headless Browser Loading
  </label>
</div>

<div class="checkbox">
  <label>
    <input type="checkbox" name="archiveWARC" disabled>Store WARC Asset
  </label>
</div>

<div class="checkbox">
  <label>
    <input type="checkbox" name="archiveIndex" checked="true">Store Index Asset
  </label>
</div>

<div class="checkbox">
  <label>
    <input type="checkbox" name="archiveGraph">Store Link Graph Asset
  </label>
</div>

<button type="submit" name="crawlingstart" value="Start New Crawl" class="btn btn-primary"/>Start</button>
</form>


{{#if actions.[0]}}
  <p>Crawl started!</p>
  {{#each actions.items}}
    Crawl ID: {{this.id}}
  {{/each}}
{{else}}
 <p>Crawl not started!</p>
{{/if}}
