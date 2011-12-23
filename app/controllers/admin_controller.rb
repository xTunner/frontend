class AdminController < ApplicationController
  before_filter :authenticate_user!
  authorize_resource :class => false

  def show
    @projects = Project.all

    # we're using mongo, right? ha!
    @projects.each do |p|
      p.builds = Build.where(vcs_url: p.vcs_url).order_by([[:build_num, :desc]]).limit(5)
    end
  end
end
