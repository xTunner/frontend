class UsersController < ApplicationController
  before_filter :authenticate_user!
  authorize_resource

  def dashboard
    @projects = current_user.projects
  end

end
