class Github

  def self.authorization_url(redirect)
    Backend.blocking_worker "circle.workers.github/authorization-url", redirect
  end

  def self.fetch_access_token(user, code)
    Backend.start_worker "circle.workers.github/fetch-github-access-token", user._id.to_s, code
  end

  def self.tentacles(command, user)
    token = user.github_access_token
    Backend.start_worker "tentacles.#{command}", "oauth_token" => token
  end

  def self.add_deploy_key(user, project, username, reponame)
    # TECHNICAL_DEBT .id.to_s should use the ruby->clojure layer
    Backend.fire_worker "circle.workers.github/add-deploy-key", username, reponame, user.github_access_token, project._id.to_s
  end
  
  def self.add_commit_hook(username, reponame, user)
    Backend.fire_worker "circle.workers.github/add-hooks", username, reponame, user.github_access_token
  end

end
