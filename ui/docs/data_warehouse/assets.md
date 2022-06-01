## Assets

###<a href="./?path={{path}}">{{path}}</a>

<pre><code>{{#each dir}}{{#if this.isdir}}<span class="glyphicon glyphicon-folder-close" aria-hidden="true"></span></i>{{else}}<span class="glyphicon glyphicon-file" aria-hidden="true"></span>{{/if}} {{#if this.isdir}}<a href="./?path={{path}}/{{this.name}}">{{this.name_p}}</a>{{else}}<a href="/{{user_id}}/api/assetget.json?path={{path}}/{{this.name}}">{{this.name_p}}</a>{{/if}} {{this.date}} {{this.size_p}}
{{/each}}</code></pre>