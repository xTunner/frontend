checkOuterPages = (url) =>
  outerPages = ["docs", "about", "privacy", "pricing"]
  for page in outerPages
    if (url.match "^/#{page}.*")
      return "/"
  return url


CI.github =
  # we encore each parammeter separately (one of them twice!) to get the right format
  authUrl: (scope=["user", "repo"]) =>
    destination = window.location.pathname
    destination = checkOuterPages(destination)
    destination = encodeURIComponent(destination)

    path = "https://github.com/login/oauth/authorize"
    client_id = window.renderContext.githubClientId
    scope = scope.join ","
    scope = encodeURIComponent(scope)

    redirect = "#{window.location.href}/auth/github?return-to=#{destination}"
    redirect = encodeURIComponent(redirect)
    "#{path}?client_id=#{client_id}&redirect_uri=#{redirect}&scope=#{scope}"
