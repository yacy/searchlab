# My Account


<div id="loginhint" class="alert alert-warning" role="alert">
  Account Service Level: {{acl.level}}<br>{{acl.description}}<br>{{acl.action}}
</div>

<form action=".">

<div class="form-group">
    <label for="email">ID</label>
    <input class="form-control" name="id" id="id" type="text" size="50" maxlength="256" value="{{authentication.id}}" disabled />
    <p class="help-block">This is your searchlab ID. We will provide functions to change this into nickname in the future.</p>
</div>

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

<div class="form-group">
    <label for="email">Github Sponsor Account</label>
    <input class="form-control" name="sponsor_github" id="sponsor_github" type="text" size="50" maxlength="256" value="{{authentication.sponsor_github}}" />
    <p class="help-block">The connected github sponsor account</p>
</div>

<p>authentication.self = {{authentication.self}}</p>

<div class="checkbox">
  <label>Use self-generated index</label>
  <input type="checkbox" name="self" id="self" {{#if authentication.self}}checked="true"{{/if}}>
  <p class="help-block">Switching this off will cause that search results are made from the complete index. Switching it on will focus on your own index only.</p>
</div>

<button type="submit" name="change" value="Change Setting" class="btn btn-primary"/>Change Setting</button>
</form>