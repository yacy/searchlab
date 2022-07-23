# Plattform Services

Searchlab provides services based on different subscription types:

<table>
<tr><th>level<th/><th>price<th/><tr/>
{{#each levels}}
{{#if this.show}}
<tr>
<td>{{this.level}}<br>{{this.description}}<td/>
<td>{{#if this.free}}free{{else}}{{this.price}} monthly subscription{{/if}}<td/>
<tr/>
{{/if}}
{{/each}}
</table>