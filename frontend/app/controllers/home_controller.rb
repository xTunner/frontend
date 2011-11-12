include Java
$CLASSPATH << File.join(Rails.root, "classes")
require "circle-0.1.0-SNAPSHOT-standalone.jar"
java_import Java::circle.Init

class HomeController < ApplicationController
  def index
    @users = User.all
#    Backend.init
  end

end
