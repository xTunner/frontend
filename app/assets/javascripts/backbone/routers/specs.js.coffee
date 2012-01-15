App.Routers.Specs = Backbone.Router.extend
  routes:
    "/gh/:user/:project/edit": "edit"

  edit: (user, project) ->
    project = "#{user}/#{project}"
    spec = new App.Models.Spec { project: project }
    spec.fetch
      success: (model, resp) ->
        new App.Views.Edit { model: model }
      error: () ->
        new Error { message: "Could not find project" }
