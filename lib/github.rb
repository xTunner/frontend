require 'rest_client'

# JRuby 1.9 broke SSL, http://jira.codehaus.org/browse/JRUBY-5529
# This monkey patch comes from https://gist.github.com/969527, referenced in that bug.
Net::BufferedIO.class_eval do
  BUFSIZE = 1024 * 16

  def rbuf_fill
    timeout(@read_timeout) {
      @rbuf << @io.sysread(BUFSIZE)
    }
  end
end



class Github

  def client_id
    case Rails.env
    when "production"
      "78a2ba87f071c28e65bb"
    when "staging"
      Nil
    else
      "586bf699b48f69a09d8c"
    end
  end

  def self.secret
    case Rails.env
    when "production"
      "98cb9262b67ad26bed9191762a23445eeb2054e4"
    when "staging"
      Nil
    else
      "1e93bdce2246fd69d9040875338b4137d525e400"
    end
  end


  def self.authorization_url(redirect)
    endpoint = "https://github.com/login/oauth/authorize"
    client_id = self.client_id
    scope = "repo" # private repositories

    query_string = {:client_id => self.client_id,
      :scope => scope,
      :redirect_uri => redirect}
      .to_query

    return "#{endpoint}?#{query_string}"
  end


  def self.fetch_access_token(code)
    response = RestClient.post "https://github.com/login/oauth/access_token", {
      :client_id => "586bf699b48f69a09d8c",
      :client_secret => self.secret,
      :code => code},
    :accept => "application/json"

    access_token = JSON.parse(@response)["access_token"]
    return access_token
  end

end
