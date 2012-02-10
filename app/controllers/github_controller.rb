require 'backend'
require 'json'

class GithubController < ApplicationController
  protect_from_forgery :except => :create
  skip_authorization_check

  def create
    json = JSON.parse params[:payload]
    after = json["after"]
    ### 0000.... is code for 'this branch has been deleted'. don't build those.
    if after != "0000000000000000000000000000000000000000"
      Backend.github_hook(json["repository"]["url"],
                          json["after"],
                          json["ref"],
                          params[:payload] # pass raw JSON string here,
                                           # because backend wants to
                                           # parse itself to get clojure
                                           # datastructures
                          )
    end
    render :nothing => true
  end
end
