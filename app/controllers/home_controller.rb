class HomeController < ApplicationController
  def index
    @signup = Signup.new
    render :layout => 'home'
  end

  def create
    Signup.create(:email => params[:email], :contact => params[:contact])
    flash[:done] = true
    redirect_to root_path
  end
end
