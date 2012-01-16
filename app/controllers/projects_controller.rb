class ProjectsController < ApplicationController
  before_filter :authenticate_user!

  # TECHNICAL_DEBT: this isn't looked up directly in the controller action, and
  # so load_and_authorize_resource wont work.

  def show
    @project = Project.from_github_name params[:project]
    authorize! :read, @project
  end

  def edit
    @project = Project.from_github_name params[:project]
    authorize! :read, @project
  end

  def update
    @project = Project.from_github_name params[:project]
    authorize! :manage, @project

    @project.update_spec(params["spec"])
    @project.save!

    # Automatically trigger another build, since after saving, you'll always want another build to run to test it.
    Backend.build(@project)

    redirect_to github_project_path(@project)
  end
end
