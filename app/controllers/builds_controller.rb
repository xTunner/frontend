require 'backend'

class BuildsController < ApplicationController
  before_filter :authenticate_user!
  respond_to :html, :json

  def create
    @project = Project.from_github_name params[:project]
    authorize! :manage, @project

    Backend.build(@project, "trigger", current_user, params[:inferred])

    render :nothing => true
  end

  def show
    @project = Project.from_github_name params[:project]
    authorize! :read, @project

    @build = @project.build_numbered params[:id]
    @logs = @build.logs

    # TECHNICAL_DEBT: we don't store timedout on old actions
    if @logs.length > 0
      l = @logs.last

      if @build.timedout || @build.infrastructure_fail
        l.timedout = true if @build.timedout
        l.infrastructure_fail = true if @build.infrastructure_fail
        l.save!
      end
    end
  end

end
