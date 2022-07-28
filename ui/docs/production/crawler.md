# Crawl Start


Start a Web crawl:

<div id="loginhint" class="alert alert-warning" role="alert">
  <a href="#" class="alert-link"><a href="/login/">Log in</a> to get access to high-level index creation rights!</a>
</div>

<form action=".">

<div class="form-group">
    <label for="crawlingURL">Start-URL</label>
    <input class="form-control" name="crawlingURL" id="crawlingURL" type="text" size="50" maxlength="256" value="" placeholder="https://..."/>
    <p class="help-block">The start-URL is a link on crawl depth 0</p>
</div>

<div class="form-group">
    <label for="crawlingDepth">Crawl Depth</label>
    <input class="form-control" name="crawlingDepth" id="crawlingDepth" type="text" size="2" maxlength="2" value="0" disabled/>
    <p class="help-block">Depth 1 means: all linked documents from the start url are included.</p>
    </label>
    <span class="badge" id="crawlingDepth_badge"><span class="glyphicon glyphicon-upload"></span> Anonymous users may only index the start document - with crawl depth 0</span>
</div>

<div class="form-group">
    <label for="collection">Collection</label>
    <input class="form-control" name="collection" id="collection" type="text" size="50" maxlength="256" value="user" disabled/>
    <p class="help-block">This is a facet tag to select an index corpus</p>
    <span class="badge" id="collection_badge"><span class="glyphicon glyphicon-upload"></span> Anonymous users are not allowed to change the collection</span>
</div>

<div class="radiobox">
<input type="radio" id="range" name="range" value="domain" checked><label for="domain">Domain</label>
<input type="radio" id="range" name="range" value="subpath"><label for="subpath">Sub-Path</label>
<input type="radio" id="range" name="range" value="wide"><label for="wide">Wide</label>
</div>

<div class="checkbox">
  <label><input type="checkbox" name="priority" id="priority_input" disabled>Priority</label>
  <span class="badge" id="priority_badge"><span class="glyphicon glyphicon-lock"></span> Only for Maintainers</span>
</div>

<div class="checkbox">
  <label><input type="checkbox" name="loaderHeadless" id="loaderHeadless_input" disabled>Headless Browser Loading</label>
  <span class="badge" id="loaderHeadless_badge"><span class="glyphicon glyphicon-lock"></span> Only for Maintainers</span>
</div>

<div class="checkbox">
  <label><input type="checkbox" name="archiveWARC" id="archiveWARC_input" disabled>Store WARC Asset</label>
  <span class="badge" id="archiveWARC_badge"><span class="glyphicon glyphicon-lock"></span> <a href="/login/">Log in</a> to get access</span>
</div>

<div class="checkbox">
  <label><input type="checkbox" name="archiveIndex" id="archiveIndex_input" checked="true" disabled>Store Index Asset</label>
  <span class="badge" id="archiveIndex_badge"><span class="glyphicon glyphicon-lock"></span> <a href="/login/">Log in</a> to get access</span>
</div>

<div class="checkbox">
  <label><input type="checkbox" name="archiveGraph" id="archiveGraph_input" disabled>Store Link Graph Asset</label>
  <span class="badge" id="archiveGraph_badge"><span class="glyphicon glyphicon-lock"></span> <a href="/login/">Log in</a> to get access</span>
</div>

<button type="submit" name="crawlingstart" value="Start New Crawl" class="btn btn-primary"/>Start</button>
</form>


<script>
document.getElementById("loginhint").classList.remove("alert-warning");
document.getElementById("loginhint").classList.add("alert-success");
document.getElementById("loginhint").textContent = "{{acl.action}}";
document.getElementById("crawlingDepth").value = {{acl.crawler.crawlingDepth.value}};
document.getElementById("crawlingDepth").disabled = {{acl.crawler.crawlingDepth.disabled}};
document.getElementById("crawlingDepth_badge").textContent = "{{acl.crawler.crawlingDepth.badge}}";
document.getElementById("collection").disabled = {{acl.crawler.collection.disabled}};
document.getElementById("collection_badge").textContent = "{{acl.crawler.collection.badge}}";
document.getElementById("priority_input").disabled = {{acl.crawler.priority.disabled}};
document.getElementById("priority_badge").textContent = "{{acl.crawler.priority.badge}}";
document.getElementById("loaderHeadless_input").disabled = {{acl.crawler.loaderHeadless.disabled}};
document.getElementById("loaderHeadless_badge").textContent = "{{acl.crawler.loaderHeadless.badge}}";
document.getElementById("archiveWARC_input").disabled = {{acl.crawler.archiveWARC.disabled}};
document.getElementById("archiveWARC_badge").textContent = "{{acl.crawler.archiveWARC.badge}}";
document.getElementById("archiveIndex_input").disabled = {{acl.crawler.archiveIndex.disabled}};
document.getElementById("archiveIndex_badge").textContent = "{{acl.crawler.archiveIndex.badge}}";
document.getElementById("archiveGraph_input").disabled = {{acl.crawler.archiveGraph.disabled}};
document.getElementById("archiveGraph_badge").textContent = "{{acl.crawler.archiveGraph.badge}}";
</script>

{{#if crawl.actions.[0]}}
  <p>Crawl started!</p>
  {{#each crawl.actions.items}}
    Crawl ID: {{this.id}}
  {{/each}}
{{else}}
 <p>Crawl not started!</p>
{{/if}}
