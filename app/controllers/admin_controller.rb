class AdminController < ApplicationController
  before_filter :authenticate_user!
  authorize_resource :class => false

  def show
    @projects = Project.order_by([[:vcs_url, :asc]])
  end
end
