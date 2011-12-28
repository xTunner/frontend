class HomeController < ApplicationController
  skip_authorization_check

  def index
    @signup = Signup.new
  end

  def create
    Signup.create(:email => params[:email], :contact => params[:contact])
    flash[:done] = true
    redirect_to root_path
  end
end
