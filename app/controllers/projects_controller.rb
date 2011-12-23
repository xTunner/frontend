class ProjectsController < ApplicationController
  before_filter :authenticate_user!

  def show
    @project = Project.from_github_name params[:project]
    authorize! :read, @project

    @recent_builds = @project.recent_builds

    render "show"
  end

  def edit
  end
end
