class ProjectsController < ApplicationController
  before_filter :authenticate_user!
  load_and_authorize_resource

  def show
    url = Backend.blocking_worker "circle.backend.github-url/canonical-url", params[:project]
    @project = Project.where(vcs_url: url).first

    # TECHNICAL_DEBT: projects should have a list of builds, but it doesnt on the clojure side.
    @recent_builds = Build.where(vcs_url: @project.vcs_url).desc.limit(20)

    render "show"
  end

end
