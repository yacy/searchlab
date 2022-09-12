# My Account

<ul class="nav nav-pills" role="tablist">
  <li role="presentation" class="active"><a href="#">Account Service Level <span class="badge">{{acl.level}}: {{acl.description}}</span></a></li>
  <li role="presentation" class="active"><a href="#">Documents <span class="badge">{{assets.size.documents}}</span></a></li>
  <li role="presentation" class="active"><a href="#">Collections <span class="badge">{{assets.size.collections}}</span></a></li>
</ul>

<p>
<div id="loginhint" class="alert alert-success"" role="alert">
  {{acl.action}}
</div>
</p>

<form action=".">

<div class="form-group">
    <label for="email">ID</label>
    <input class="form-control" name="id" id="id" type="text" size="50" maxlength="256" value="{{authentication.id}}" disabled />
    <p class="help-block">This is your searchlab ID. We will provide functions to change this into nickname in the future.</p>
</div>

{{#if acl.authenticated}}

<div class="form-group">
    <label for="email">Email Address</label>
    <input class="form-control" name="email" id="email" type="text" size="50" maxlength="256" value="{{authentication.email}}" disabled />
    <p class="help-block">You are identified with this email address. The adress is not shown anywhere to users of this portal.</p>
</div>

<div class="form-group">
    <label for="email">Contact Name</label>
    <input class="form-control" name="name" id="name" type="text" size="50" maxlength="256" value="{{authentication.name}}" disabled />
    <p class="help-block">Your Contact/Contract Name. This is not shown anywhere to users of this portal.</p>
</div>

<div class="form-group">
    <label for="email">Patreon Account</label>
    <input class="form-control" name="sponsor_patreon" id="sponsor_patreon" type="text" size="50" maxlength="256" value="{{authentication.sponsor_patreon}}" />
    <p class="help-block">The connected patreon account</p>
</div>

<div class="checkbox">
  <label>Verified Patreon Account</label>
  <input type="checkbox" name="sponsor_patreon_verified" id="sponsor_patreon_verified" {{#if authentication.sponsor_patreon_verified}}checked="false"{{/if}} disabled>
  <p class="help-block">If this is switched on, your patreon account was verified.</p>
</div>

<div class="form-group">
    <label for="email">Github Sponsor Account</label>
    <input class="form-control" name="sponsor_github" id="sponsor_github" type="text" size="50" maxlength="256" value="{{authentication.sponsor_github}}" />
    <p class="help-block">The connected github sponsor account</p>
</div>

<div class="checkbox">
  <label>Verified Github Sponsor Account</label>
  <input type="checkbox" name="sponsor_github_verified" id="sponsor_github_verified" {{#if authentication.sponsor_github_verified}}checked="false"{{/if}} disabled>
  <p class="help-block">If this is switched on, your github sponsor account was verified. If this is off and you believe it should be on, try to log out and log in again!</p>
</div>

<br/>

<div class="checkbox">
  <label>Use self-generated index</label>
  <input type="checkbox" name="self" id="self" {{#if authentication.self}}checked="true"{{/if}}>
  <p class="help-block">Switching this off will cause that search results are made from the complete index. Switching it on will focus on your own index only.</p>
</div>

<button type="submit" name="change" value="Change Setting" class="btn btn-primary"/>Change Setting</button>


<br/><br/><br/><br/><br/>

<h2>Danger Zone</h2>

<p>
You can terminate your usage of the Searchlab and delete your personal data here. When you hit the following button, there will be no further check if this is a mistake or not, your account will be deleted permanently without the option to revert this action. Your name and email address as well as all settings visible here will be erased and removed from our database, not just marked as deleted. The crawled data, however, will stay in the search index but will be a candidate for further cleanup later.
</p>

<button type="submit" name="delete" value="Delete Account" class="btn btn-danger"/>Delete Account</button>
{{else}}

<div class="checkbox">
  <label>Use self-generated index</label>
  <input type="checkbox" name="self" id="self" {{#if authentication.self}}checked="true"{{/if}} disabled>
  <p class="help-block">Switching this off will cause that search results are made from the complete index. Switching it on will focus on your own index only.</p>
</div>

{{/if}}

</form>