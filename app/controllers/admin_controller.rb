class AdminController < ApplicationController
  before_filter :authenticate_user!
  authorize_resource :class => false

  def show

    @projects = Project.order_by([[:vcs_url, :asc]])
    @builds = Build.order_by([[:start_time, :desc]]).limit(20).all
  end

  def sandbox
    SimpleMailer.test_email
  end

end
