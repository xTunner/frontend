require 'backend'

class BuildsController < ApplicationController
  respond_to :html, :json

  def create
    url = Backend.blocking_worker "circle.backend.github-url/canonical-url", params[:project]
    @project = Project.where(vcs_url: url).first

    Backend.build(@project)

    render :text => ""

  end

  def show
    url = Backend.blocking_worker "circle.backend.github-url/canonical-url", params[:project]
    @project = Project.where(vcs_url: url).first

    id = params[:id]
    @build = Build.where(vcs_url: url, build_num: id).first

    @logs = @build.logs
  end

end
