# Welcome to 11ty Searchlab Example

{% set css %}
  {% include "searchlab.css" %}
{% endset %}


<div class="c">
    <h1>Searchlab&nbsp;/ Search&nbsp;Template</h1>

    <div class="row">
        <input type="text" name="query" id="query" class="card w-75" autofocus="autofocus" onFocus="this.select()" onClick="document.getElementById('startRecord').value=0;document.getElementById('query').value='';"/>&nbsp;
        <input type="hidden" name="startRecord" id="startRecord" value="0"/>
        <button id="search" onclick="return getapi()" class="btn primary">search</button>
    </div>

    <!-- template for result list used by t.js -->
    <script type="t/template" id="resulttemplate">
        {{=results}}
        {{@items}}
        <div class="panel panel-default">
            <h4 class="panel-title"><a href="{{=_val.link}}" target="_blank">{{=_val.title}}</a></h4>
            <p>{{=_val.description}}</p>
            <p><a href="{{=_val.link}}" target="_blank">{{=_val.link}}</a><br>{{=_val.sizename}}</p>
        </div>
        {{/@items}}
    </script>
    <div id="result"></div>

    <!-- template for pagination bar used by t.js -->
    <script type="t/template" id="paginationtemplate">
        <div class="btn-group" role="group" aria-label="pagination">
            {{@items}}
                <button type="button" class="btn {{_val.same}}primary{{/_val.same}} btn-xs" onClick="document.getElementById('startRecord').value={{=_val.startRecord}}; getapi();">{{=_val.page}}</button>
            {{/@items}}
        </div>
    </script>
    <div id="pagination"></div>
    <a href="http://searchlab.eu">Get a personalized search from searchlab.eu</a>
</div>

{% set js %}
  {% include "searchlab.js" %}
{% endset %}