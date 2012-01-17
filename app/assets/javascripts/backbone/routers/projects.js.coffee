App.Routers.Projects = Backbone.Router.extend
  routes:
    "/gh/:user/:project/edit*page": "edit"

  edit: (user, project, page) ->
    page = page.substring 1 # remove starting "#"
    if page == ""
      page = "settings"

    project_name = "#{user}/#{project}"
    project = new App.Models.Project { project: project_name }

    project.fetch
      success: (project, resp) ->
        new App.Views.EditProject { model: project, page: page }
