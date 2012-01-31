require 'backend'

class BuildsController < ApplicationController
  before_filter :authenticate_user!
  respond_to :html, :json

  def create
    @project = Project.from_github_name params[:project]
    authorize! :manage, @project

    Backend.build(@project, params[:inferred])

    render :nothing => true
  end

  def show
    @project = Project.from_github_name params[:project]
    authorize! :read, @project

    @build = @project.build_numbered params[:id]
    @logs = @build.logs
  end

end
