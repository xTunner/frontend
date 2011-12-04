class Github

  def self.authorization_url(redirect)
    endpoint = "https://github.com/login/oauth/authorize"
    client_id = self.client_id
    scope = "repo" # private repositories

    query_string = {:client_id => client_id,
      :scope => scope,
      :redirect_uri => redirect}
      .to_query

    return "#{endpoint}?#{query_string}"
  end

  def self.fetch_access_token(code)
    id = Backend.start_worker "circle.workers.github/fetch-github-access-token", code
    Backend.wait_for_worker(id)
  end
end
