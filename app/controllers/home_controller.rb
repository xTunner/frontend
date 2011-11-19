class HomeController < ApplicationController
  def index
    @signup = Signup.new

    # clj = JRClj.new
    # clj.inc 0

    # circle = JRClj.new "circle.init"

    # db = JRClj.new "circle.db"
    # db.run "circle.db/init"

    # circle.run "circle.init/-main"
    # circle.init

    # JRClj.new("circle.util.time").ju_now
  end

  def create
    Signup.create(:email => params[:email], :contact => params[:contact])
    flash[:done] = true
    redirect_to root_path
  end
end
