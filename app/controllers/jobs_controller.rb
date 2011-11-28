require 'backend'

class JobsController < ApplicationController
  respond_to :html, :json

  def create
    @project = Project.find(params[:project_id])
    @job = @project.jobs.create!
    Backend.build(@project)

    respond_with(@project, @job)
  end

  def show
    @job = Job.find(params[:id])
  end
end
