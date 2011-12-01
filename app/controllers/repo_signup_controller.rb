require 'rest_client'

# JRuby 1.9 broke SSL, http://jira.codehaus.org/browse/JRUBY-5529
# This monkey patch comes from https://gist.github.com/969527, reference in that bug.
Net::BufferedIO.class_eval do
  BUFSIZE = 1024 * 16

  def rbuf_fill
    timeout(@read_timeout) {
      @rbuf << @io.sysread(BUFSIZE)
    }
  end
end


class RepoSignupController < ApplicationController
  before_filter :authenticate_user!

  # The is the page where users give us access to their repos. There are several ways to get here:
  #
  # - We send the user an invite, using devise. At this point, they are a real user, and the token
  #   handles the authentication. We'll just solve this case for now. We get their email from the
  #   Signup object.
  #
  # - We get a user from Exceptional. They have a token and an email address (slightly obscured). We
  #   store the token, and manually verify with Ben that we can charge them. We set them up as a
  #   normal user.
  #   TODO: this requires them to have a password with us!! Not completely friendly.
  #
  # - A user signs up on the homepage. We likely won't handle that here.
  def new
    # Simplest thing first, just display the form.
    github_url = "https://github.com/login/oauth/authorize"
#    client_id = "78a2ba87f071c28e65bb" # Circle
    client_id = "586bf699b48f69a09d8c" # Circle_test
    redirect_URI = hooks_github_callback_url # urI not urL
    scope = "repo" # private repositories
    query_string = {:client_id => client_id,
      :scope => scope,
      :redirect_uri => redirect_URI}
      .to_query

    @url = "#{github_url}?#{query_string}"


    @project = Project.new
  end


  def github_reply
    code = params[:code]
    access_token = params[:access_token]
    if code

#    secret = "98cb9262b67ad26bed9191762a23445eeb2054e4" # Circle
      secret = "1e93bdce2246fd69d9040875338b4137d525e400" # Circle_test
      begin
        @response = RestClient.post "https://github.com/login/oauth/access_token", {
          :client_id => "586bf699b48f69a09d8c",
          :client_secret => secret,
          :code => code},
        :accept => "application/json"

        @access_token = JSON.parse(@response)["access_token"]

        # store the token in the user
        current_user.github_access_token = @access_token
        current_user.save!
      rescue => @e
      end
    end
  end
end
