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
    <input class="form-control" name="name" id="name" type="name" size="50" maxlength="256" value="{{authentication.name}}" disabled />
    <p class="help-block">Your Contact/Contract Name. This is not shown anywhere to users of this portal.</p>
</div>

<button type="submit" name="change" value="Change Setting" class="btn btn-primary"/>Change Setting</button>
</form>