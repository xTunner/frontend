CI.github =
  authUrl: (scope=["user", "repo"]) =>
    # we encore each parammeter separately (one of them twice!) to get the right format
    path = "https://github.com/login/oauth/authorize"
    client_id = window.renderContext.githubClientId
    scope = scope.join ","
    scope = encodeURIComponent(scopes)
    destination = encodeURIComponent(window.location.pathname)
    redirect = "#{window.location.origin}/auth/github?return-to=#{destination}"
    redirect = encodeURIComponent(redirect)
    "#{path}?client_id=#{client_id}&redirect_uri=#{redirect}&scope=#{scope}"
