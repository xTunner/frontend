class AdminController < ApplicationController
  before_filter :authenticate_user!
  authorize_resource :class => false

  def show
    @builds = Build.order_by([[:start_time, :desc]]).page params[:page]
    @users = User.non_guests
  end

end
