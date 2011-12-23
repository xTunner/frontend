class AdminController < ApplicationController
  before_filter :authenticate_user!
  authorize_resource :class => false

  def show
    @projects = Project.all
    @projects.each { |p| p.include_builds! }
  end
end
