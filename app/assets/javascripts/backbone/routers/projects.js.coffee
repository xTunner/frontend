App.Routers.Projects = Backbone.Router.extend
  routes:
    "*page": "edit" # note that this isn't actually the blank url because Backbone's root url is set.

  edit: (page) ->

    [dc, dc, user, project, dc] = App.base_url.split /\// # dk means 'dontcare'
    project_name = "#{user}/#{project}"

    if App.view
      App.view.set_page page
    else
      App.model = new App.Models.Project { project: project_name }
      App.model.fetch
        success: (model, resp) =>
          App.view = new App.Views.EditProject { model: App.model }
          App.view.set_page page
