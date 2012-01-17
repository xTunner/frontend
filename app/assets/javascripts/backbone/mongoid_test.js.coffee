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
    @router = new App.Routers.Projects()
    Backbone.history.start { root: "/", pushState: true }

    href = window.location.pathname + window.location.hash
    @router.navigate(href, true)
