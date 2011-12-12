class ApplicationController < ActionController::Base
  protect_from_forgery
  layout :layout_by_resource

  protected

  # Specify the layout for the controller; rails looks for a layout with the
  # name of the controller first, and only comes here when it can't find it. For
  # devise, we use the "outer application" layout, currently named "join" :-/
  def layout_by_resource
    if devise_controller?
      "join"
    else
      "application"
    end
  end

end
