# Plattform Services

Searchlab provides services based on different subscription types:

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
<tr>
<td>{{this.level}}<br>{{this.description}}<td/>
<td>{{#if this.free}}free{{else}}{{this.price}} monthly subscription{{/if}}<td/>
<td>Maximum&nbsp;Crawl&nbsp;Depth:&nbsp;{{this.crawler.crawlingDepth.max}}{{#if this.crawler.archiveIndex.enabled}},<br>index download archive {{this.index.index_mb.max}}MB {{/if}}<td/>
<td>Maximum&nbsp;Documents:&nbsp;{{this.index.documents.max}}<td/>
<td>Maximum&nbsp;Queries: {{this.queries.search.frequency_count.[0]}}/minute, {{this.queries.search.frequency_count.[1]}}/5&nbsp;minutes, {{this.queries.search.frequency_count.[2]}}/hour<td/>
<tr/>
{{/if}}
{{/each}}
</table>