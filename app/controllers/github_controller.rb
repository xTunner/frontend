require 'backend'
require 'json'

class GithubController < ApplicationController
  protect_from_forgery :except => :create
  skip_authorization_check

  def create
    json = JSON.parse params[:payload]
    Backend.github_hook(json["repository"]["url"],
                        json["after"],
                        json["ref"],
                        params[:payload] # pass raw JSON string here,
                                         # because backend wants to
                                         # parse itself to get clojure
                                         # datastructures
)
    render :nothing => true
  end
end
