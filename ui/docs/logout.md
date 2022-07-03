# Logout

You are logged out!

<script>
  function delay(time) {
    return new Promise(resolve => setTimeout(resolve, time));
  }
  delay(1500).then(() => window.location.href = "/en/");
</script>