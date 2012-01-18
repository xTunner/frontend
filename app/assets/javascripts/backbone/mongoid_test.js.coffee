#= require_self
#= require_tree ./templates
#= require_tree ./models
#= require_tree ./views
#= require_tree ./routers

window.App =
  Models: {}
  Collections: {}
  Routers: {}
  Views: {}

  init: () ->
    # Get the project and put it somewhere accessible to all.
    @router = new App.Routers.Projects()

    # We can simplify all of this by using '/' as the base_url
    @base_url = window.location.pathname
    # pushState is false because we like having the '#' in the URL for now
    Backbone.history.start { root: @base_url, pushState: false }
