# Plattform Services

Searchlab provides services based on different subscription types:

{{#with aclall}}
<table>
<tr>
<th>level<th/>
<th>price<th/>
<th>crawler<th/>
<th>index size<th/>
<th>queries<th/>
<tr/>

{{#each levels}}
{{#if this.show}}

<tr><td>

{{this.level}}<br>{{this.description}}

<td/><td>

{{#if this.free}}free{{else}}{{this.price}} monthly subscription{{/if}}

<td/><td>

Maximum&nbsp;Crawl&nbsp;Depth:&nbsp;{{this.crawler.crawlingDepth.max}}
{{#if this.crawler.range.enabled}}, domain/subdomain/wide&nbspcrawls{{else}}, domain-only crawls{{/if}}
{{#if this.crawler.loaderHeadless.enabled}}, headless&nbsp;loader{{/if}}
{{#if this.crawler.collection.enabled}}, collections&nbsp;enabled{{/if}}

<td/><td>

Maximum&nbsp;Documents:&nbsp;{{this.index.documents.max}}
{{#if this.crawler.archiveWARC.enabled}},<br>WARC&nbsp;download&nbsp;archive&nbsp;{{this.index.warc_mb.max}}MB{{/if}}
{{#if this.crawler.archiveIndex.enabled}},<br>index&nbsp;download&nbsp;archive&nbsp;{{this.index.index_mb.max}}MB{{/if}}
{{#if this.crawler.archiveGraph.enabled}},<br>Graph&nbsp;download&nbsp;archive&nbsp;{{this.index.graph_mb.max}}MB{{/if}}

<td/><td>

Maximum&nbsp;Queries: {{this.queries.search.frequency_count.[0]}}/minute, {{this.queries.search.frequency_count.[1]}}/5&nbsp;minutes, {{this.queries.search.frequency_count.[2]}}/hour

<td/><tr/>
{{/if}}
{{/each}}

</table>
{{/with}}


{{#if acl.authenticated}}

{{#if acl.free}}
<p>Become a searchlab patron/sponsor and top up to the next level!</p>
<a href="https://www.patreon.com/bePatron?u=185903"><img src="../../img/sponsor_patreon.png" height="128" align="left" style="padding-left:12%;padding-right:20px"></a>
<a href="https://github.com/users/Orbiter/sponsorship"><img src="../../img/sponsor_github.png" height="128" align="right" style="padding-right:12%"></a>
<br><br><br><br><br><br>
<p>Once you are a searchlab sponsor, you can assign your sponsorship at your <a href="/home/">account settings</a></p>
{{/if}}

{{else}}
<p><a href="/login/">Log In</a> to get increased access grants!</p>
{{/if}}
