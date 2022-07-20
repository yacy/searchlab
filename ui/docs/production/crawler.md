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
grade_level={{grade_level}};

// L00_Everyone
// L01_Anonymous

// L02_Authenticated
if (grade_level == 2) {
    document.getElementById("loginhint").classList.remove("alert-warning");
    document.getElementById("loginhint").classList.add("alert-success");
    document.getElementById("loginhint").textContent = "You are an authenticated, free-service grade user";
    document.getElementById("crawlingDepth").disabled = false;
    document.getElementById("crawlingDepth").value = 1;
    document.getElementById("crawlingDepth_badge").textContent = "";
    document.getElementById("archiveIndex_input").disabled = false;
    document.getElementById("archiveIndex_badge").textContent = "";
}

// L03_Primary
// L04_Level_One
// L05_Level_Five
// L06_Level_Twentyfive
// L07_Level_Fifty
// L08_Level_Twohundred


// L09_Maintainer
if (grade_level == 9) {
    document.getElementById("loginhint").classList.remove("alert-warning");
    document.getElementById("loginhint").classList.add("alert-success");
    document.getElementById("loginhint").textContent = "You have full rights for all options!";
    document.getElementById("crawlingDepth").disabled = false;
    document.getElementById("crawlingDepth").value = 3;
    document.getElementById("crawlingDepth_badge").textContent = "";
    document.getElementById("collection").disabled = false;
    document.getElementById("collection_badge").textContent = "";
    document.getElementById("priority_input").disabled = false;
    document.getElementById("priority_badge").textContent = "";
    document.getElementById("loaderHeadless_input").disabled = false;
    document.getElementById("loaderHeadless_badge").textContent = "";
    document.getElementById("archiveWARC_input").disabled = false;
    document.getElementById("archiveWARC_badge").textContent = "";
    document.getElementById("archiveIndex_input").disabled = false;
    document.getElementById("archiveIndex_badge").textContent = "";
    document.getElementById("archiveGraph_input").disabled = false;
    document.getElementById("archiveGraph_badge").textContent = "";
}
</script>

{{#if actions.[0]}}
  <p>Crawl started!</p>
  {{#each actions.items}}
    Crawl ID: {{this.id}}
  {{/each}}
{{else}}
 <p>Crawl not started!</p>
{{/if}}
