# Login

User Accounts in the Searchlab are used to personalize search indexes:

- every user may generate several Corpora (a Corpus is a document set generted by web crawls)
- users can provide access to their search corpus using their ID and a corpus name
- user-IDs may become group IDs where several users are able to modify the group content
- a future extension will allow named user-IDs so search indexes can be accessed also with names, not only IDs

## Accounts

We currently have no user-owned accounts, only virtual accounts with user-IDs that cannot be owned by anyone.

This will change in the future, by now you can only

- re-use a previously generated user-id that you know, or
- generate a new one.

<form action="#" class="navbar-form navbar-left">
  <div class="form-group">
    <input type="text" id="idinput" onFocus="this.select()" class="form-control" placeholder="id">
  </div>
  <button type="submit" id="genidbtn" class="btn btn-info btn-sm" onclick="return getid()">Generate ID</button>
  <button type="submit" id="loginbtn" class="btn btn-default btn-sm" disabled="disabled" onclick="return login()">Login</button>
</form></br></br>

You can keep your id private or you can share it publicly. Because every id may be owned/lost or never be used again, we treat user accounts without
ownership right now as temporary and expendable entity. Please be prepared that your id may be deleted by the platform at any time.


<script>
    var input = document.getElementById('idinput');
    input.addEventListener('keyup', verifyid);
    function getid() {
        const xhr = new XMLHttpRequest(); xhr.open('GET', '/en/api/aaa/id_generator.json');
        xhr.setRequestHeader('Content-type', 'application/json'); xhr.responseType = 'json'; xhr.send();
        xhr.onload = function() {
            document.getElementById("idinput").value = xhr.response.id;
            document.getElementById("loginbtn").disabled = "";
        }
    }
    function verifyid() {
        const id = document.querySelector('#idinput').value;
        const xhr = new XMLHttpRequest();
        xhr.open('GET', '/en/api/aaa/id_validation.json?id=' + id);
        xhr.setRequestHeader('Content-type', 'application/json');
        xhr.responseType = 'json';
        xhr.send();
        xhr.onload = function() {
            if (xhr.response.valid) {
                document.getElementById("loginbtn").disabled = "";
                return false;
            } else {
                document.getElementById("loginbtn").disabled = "disabled";
                return true;
            }
        }
    }
    function login() {
        verifyid();
        const disabled = document.getElementById("loginbtn").disabled
        if (disabled) return;
        const id = document.querySelector('#idinput').value;
        window.location.href = "/" + id + "/";
        return false;
    }
</script>