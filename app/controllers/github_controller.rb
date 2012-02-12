require 'backend'
require 'json'

class GithubController < ApplicationController
  protect_from_forgery :except => :create
  skip_authorization_check

  def create
    Backend.github_hook(params[:payload])
    render :nothing => true
  end
end
