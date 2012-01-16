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

    if @project["setup"]
      @spec = @project.specs[0]
      @spec.setup = @project.setup
      @spec.dependencies = @project.dependencies
      @spec.compile = @project.compile
      @spec.test = @project.test
    else
      @spec = @project.specs[0] || @project.specs.create
    end
  end

  def update
    @project = Project.from_github_name params[:project]
    authorize! :manage, @project

    if @project["setup"]
      allowed_keys = ["setup", "dependencies", "compile", "test"]
      attrs = params["spec"].select { |k,v| allowed_keys.include?(k) }
      @project.update_attributes(attrs)
      @project.save!
    else
      @project.specs[0].update_attributes(params["spec"])
      @project.specs[0].save!
    end

    # Automatically trigger another build, since after saving, you'll always want another build to run to test it.
    Backend.build(@project)

    redirect_to github_project_path(@project)
  end
end
