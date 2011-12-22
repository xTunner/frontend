require 'backend'

class BuildsController < ApplicationController
  respond_to :html, :json

  def create
    url = Backend.blocking_worker "circle.backend.github-url/canonical-url", params[:project]
    @project = Project.where(vcs_url: url).first

    Backend.build(@project)

    render :text => ""

  end

end
