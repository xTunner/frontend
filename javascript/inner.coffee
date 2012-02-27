class Project
  constructor: (vcs_url) ->
    @vcs_url = ko.observable(vcs_url)


class DashboardViewModel
  constructor: () ->
    @projects = ko.observableArray()
    $.getJSON '/api/v1/projects', (data) =>
      for d in data
        @addProject(d["vcs_url"])


  addProject: (vcs_url) ->
    @projects.push(new Project(vcs_url))

ko.applyBindings(new DashboardViewModel())