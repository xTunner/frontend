App.Routers.Specs = Backbone.Router.extend
  routes:
    "gh/:project/edit": "edit"

  edit: (project) ->
    spec = new Spec { project: project }
    spec.fetch
      success: (model, resp) ->
        new App.Views.Edit { model: spec }
      error: () ->
        new Error { message: "Could not find project" }
