class UsersController < ApplicationController
  before_filter :authenticate_user!
  authorize_resource

  def dashboard
    @projects = current_user.projects
    @recent_builds = current_user.latest_project_builds
  end

end
