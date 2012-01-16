App.Routers.Projects = Backbone.Router.extend
  routes:
    "/gh/:user/:project/edit": "edit"

  edit: (user, project) ->
    project = "#{user}/#{project}"
    spec = new App.Models.Project { project: project }
    spec.fetch
      success: (model, resp) ->
        new App.Views.EditProject { model: model }
      error: (resp) ->
        # TODO: doesn't actually do anything
        new Error { message: "Could not find project" }
