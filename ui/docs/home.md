# My Account

<form action=".">

<div class="form-group">
    <label for="email">Email Address</label>
    <input class="form-control" name="email" id="email" type="text" size="50" maxlength="256" value="" disabled />
    <p class="help-block">You are identified with this email address. The adress is not shown anywhere to users of this portal.</p>
</div>

<button type="submit" name="change" value="Change Setting" class="btn btn-primary"/>Change Setting</button>
</form>

{{#each .}}
<div class="card" style="width:280px; height:346px; border-radius: 8px; display: flex; flex-flow: column; background-color:#4E5D6C; margin-right:12px; margin-bottom:12px; padding:12px; position:relative; float:left; display:block; overflow: hidden;">
  <a href="../../app/{{this.path}}/" target="_blank" rel="noopener noreferrer" >
    <img src="../../app/{{this.path}}/screenshot.png" class="card-img-top" style="border-radius: 4px;" alt="{{this.name}}" width="256" height="256">
  </a>
  <div class="card-body" style="flex:auto;">
    <h5 class="card-title">{{this.name}}</h5>
    <p class="card-text">{{this.headline}}</p>
  </div>
</div>

{{/each}}