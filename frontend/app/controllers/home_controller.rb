class HomeController < ApplicationController
  def index
    @users = User.all

    clj = JRClj.new
    clj.inc 0

    circle = JRClj.new "circle.Init"

    db = JRClj.new "circle.db"
    db.run "circle.db/init"

    circle.run "circle.Init/-main"
    circle.init
  end
end
