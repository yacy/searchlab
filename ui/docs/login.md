# Login

User Accounts in the Searchlab are used to personalize search indexes:

- every user may generate several Corpora (a Corpus is a document set generted by web crawls)
- users can provide access to their search corpus using their ID and a corpus name
- user-IDs may become group IDs where several users are able to modify the group content
- a future extension will allow named user-IDs so search indexes can be accessed also with names, not only IDs

## Authorized Accounts

You can currently only sign-up using an existing GitHub or Patreon account.
When you log with one of these accounts you must agree with the GDPR Compliance as declared in the footer below:

<form action="/en/aaaaa/github_get_auth/" class="navbar-form navbar-left">
  <button type="submit" id="login_with_github" class="btn btn-default btn-sm" style="background-color: #000000; border-radius:8px; font-size:18px; width:300px;"><img src="/img/login_with_github.png" width="32" height="32">&nbsp;&nbsp;Login with Github</button>
</form>
</br></br>

<form action="/en/aaaaa/patreon_get_auth/" class="navbar-form navbar-left">
  <button type="submit" id="login_with_github" class="btn btn-default btn-sm" style="background-color: #FF424D; border-radius:8px; font-size:18px; width:300px;"><img src="/img/login_with_patreon.png" width="32" height="32">&nbsp;&nbsp;Connect with Patreon</button>
</form>
</br></br>

We are also preparing sign-up with the following OAuth providers:


<form action="/en/aaaaa/twitter_get_auth/" class="navbar-form navbar-left">
  <button type="submit" id="login_with_github" class="btn btn-default btn-sm" style="background-color: #1DA1F2; border-radius:8px; font-size:18px; width:300px;" disabled="disabled"><img src="/img/login_with_twitter.png" width="32" height="32">&nbsp;&nbsp;Login with Twitter</button>
</form>
</br></br>

<form action="/en/aaaaa/twitch_get_auth/" class="navbar-form navbar-left">
  <button type="submit" id="login_with_github" class="btn btn-default btn-sm" style="background-color: #9147FF; border-radius:8px; font-size:18px; width:300px;" disabled="disabled"><img src="/img/login_with_twitch.png" width="32" height="32">&nbsp;&nbsp;Login with Twitch</button>
</form>
</br></br>

We do not provide sign-up by email here yet.

## Demo Accounts with limited Authorization

We also provide anonymous authentication using temporary User IDs for demo purpose.
Existing IDs can be submitted here to access any account, even authorized ones, but without access to changes to those accounts.

Enter existing IDs or generate a new one:

<form action="#" class="navbar-form navbar-left">
  <div class="form-group">
    <input type="text" id="idinput" onFocus="this.select()" class="form-control" placeholder="id">
  </div>
  <button type="submit" id="genidbtn" class="btn btn-info btn-sm" onclick="return getid()">Generate ID</button>
  <button type="submit" id="loginbtn" class="btn btn-default btn-sm" disabled="disabled" onclick="return login()">Login</button>
</form></br></br>

You can keep your id private or you can share it publicly. Because every id may be owned/lost or never be used again, we treat user accounts without ownership right now as temporary and expendable entity. Please be prepared that your id may be deleted by the platform at any time.

<script>
    var input = document.getElementById('idinput');
    input.addEventListener('keyup', verifyid);
    
    function getid() {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', '/en/api/aaaaa/id_generator.json');
        xhr.responseType = 'json';
        xhr.send();
        xhr.onload = function() {
            document.getElementById("idinput").value = xhr.response.id;
            document.getElementById("loginbtn").disabled = "";
        }
        return false;
    }
    function verifyid() {
        var id = document.querySelector('#idinput').value;
        var xhr = new XMLHttpRequest();
        xhr.open('GET', '/en/api/aaaaa/id_validation.json?id=' + id);
        xhr.responseType = 'json';
        xhr.send();
        xhr.onload = function() {
            document.getElementById("loginbtn").disabled = xhr.response.valid ? "" : "disabled";
        }
        return false;
    }
    function login() {
        verifyid();
        if (document.getElementById("loginbtn").disabled) return;
        window.location.href = "/" + document.querySelector('#idinput').value + "/";
        return false;
    }
</script>