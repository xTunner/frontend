class ProjectsController < ApplicationController
  before_filter :authenticate_user!

  # TECHNICAL_DEBT: this isn't looked up directly in the contrller action, and
  # so load_and_authorize_resource wont work. So we need to authorize everything
  # manually. Dangerous and easy to miss one! A solution is to add the
  # configuration setting which creates an error every time we miss one, that's
  # a good idea.

  def show
    @project = Project.from_github_name params[:project]
    authorize! :read, @project

    @recent_builds = @project.recent_builds

    render "show"
  end

  def edit
    @project = Project.from_github_name params[:project]
    authorize! :read, @project
  end
end
