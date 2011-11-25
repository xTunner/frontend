class UsersController < ApplicationController

  before_filter :authenticate_user!

  def show
    @projects = Project.all
  end

end
