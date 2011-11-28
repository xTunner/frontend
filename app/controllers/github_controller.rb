require 'backend'
require 'json'

class GithubController < ApplicationController
  protect_from_forgery :except => :create

  def create
    json = JSON.parse params[:payload]
    Backend.github_hook(json["repository"]["url"],
                            json["after"],
                            json["ref"],
                            json)
    render :nothing => true
  end
end
