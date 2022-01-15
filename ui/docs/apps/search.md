## Search
<script>
//var search_api = "http://localhost:8400/api/yacysearch.json";
var search_api = "/api/yacysearch.json";
</script>

<script>
// t.min.js from https://github.com/jasonmoo/t.js (C) MIT License by Jason Mooberry 
(function(){function c(a){this.t=a}function l(a,b){for(var e=b.split(".");e.length;){if(!(e[0]in a))return!1;a=a[e.shift()]}return a}function d(a,b){return a.replace(h,function(e,a,i,f,c,h,k,m){var f=l(b,f),j="",g;if(!f)return"!"==i?d(c,b):k?d(m,b):"";if(!i)return d(h,b);if("@"==i){e=b._key;a=b._val;for(g in f)f.hasOwnProperty(g)&&(b._key=g,b._val=f[g],j+=d(c,b));b._key=e;b._val=a;return j}}).replace(k,function(a,c,d){return(a=l(b,d))||0===a?"%"==c?(new Option(a)).innerHTML.replace(/"/g,"&quot;"):
a:""})}var h=/\{\{(([@!]?)(.+?))\}\}(([\s\S]+?)(\{\{:\1\}\}([\s\S]+?))?)\{\{\/\1\}\}/g,k=/\{\{([=%])(.+?)\}\}/g;c.prototype.render=function(a){return d(this.t,a)};window.t=c})();
</script>

<!-- template for result list used by t.js -->
<script type="t/template" id="resulttemplate">
{{=results}}
{{@items}}
<div class="panel panel-default">
  <div class="panel-heading">
    <h3 class="panel-title"><a href="{{=_val.link}}" target="_blank">{{=_val.title}}</a></h3>
  </div>
  <div class="panel-body">
    {{=_val.description}}
  </div>
  <div class="panel-footer">
    <a href="{{=_val.link}}" target="_blank">{{=_val.link}}</a><br>{{=_val.size}} {{=_val.sizename}}
  </div>
</div>
{{/@items}}
</script>

<!-- template for pagination bar -->
<script type="t/template" id="paginationtemplate">
<div class="btn-group" role="group" aria-label="pagination">
{{@items}}
  <button type="button" class="btn btn-{{=_val.style}} btn-xs" onClick="document.getElementById('startRecord').value={{=_val.startRecord}}; getapi();">{{=_val.page}}</button>
{{/@items}}
</div>
</script>

<!-- search request form -->
<div class="input-group">
<input type="text" name="query" id="query" class="form-control" size="80" maxlength="100" autofocus="autofocus" onFocus="this.select()" onClick="document.getElementById('startRecord').value=0;document.getElementById('query').value='';"/>
<input type="hidden" name="startRecord" id="startRecord" value="0"/>
<span class="input-group-btn">
<button id="search" onclick="return getapi()" class="btn btn-default">search</button>
</span>
</div>

<script>
  // XHR request to evaluate search request
  function getapi() {
    const query = document.querySelector('#query').value;
    const startRecord = parseInt(document.querySelector('#startRecord').value); // starts at 0
    const xhr = new XMLHttpRequest();
    xhr.open('GET', search_api + '?startRecord=' + startRecord + '&query=' + query);
    xhr.setRequestHeader('Content-type', 'application/json');
    xhr.responseType = 'json';
    xhr.send();
    xhr.onload = function() {
      var channel = xhr.response.channels[0];
      var pages = Math.floor(channel.totalResults / channel.itemsPerPage) + 1;
      channel["results"] = channel.totalResults == 0 ? "" : "<p>" + channel.totalResults + " hits, page " + (Math.floor(startRecord / channel.itemsPerPage) + 1) + " of " + pages + "</p>";
      // result list 
      document.getElementById("result").innerHTML = new t(document.getElementById('resulttemplate').innerHTML).render(channel);
      // page navigation
      document.getElementById("pagination").innerHTML = new t(document.getElementById('paginationtemplate').innerHTML).render({"items": channel.pagenav});
    }
  }
  // event listener on query field to trigger search button when enter is hit
  var input = document.getElementById("query");
  input.addEventListener("keyup", function(event) {if (event.keyCode === 13) {return getapi();}});
</script>

<div id="result"></div>
<div id="pagination"></div>
