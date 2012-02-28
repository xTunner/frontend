class Base
  komp: (args...) =>
    ko.computed args...

class Project extends Base
  constructor: (vcs_url, status) ->
    @vcs_url = vcs_url
    @status = status
    @project_name = @komp => @vcs_url.substring(19)
    @project_path = @komp => '/gh/' + @project_name()
    @edit_link = @komp => @project_path() + '/edit'
    @latest_build = @komp => "z"


class DashboardViewModel extends Base

  constructor: ->
    @projects = ko.observableArray()

    $.getJSON '/api/v1/projects', (data) =>
      for d in data
        @addProject(d.vcs_url, d.status, d.project_url)


  projects_with_status: (filter) =>
    @komp =>
      p for p in @projects() when p.status == filter



  addProject: (vcs_url, status) =>
    @projects.push(new Project(vcs_url, status))



window.dashboardViewModel = new DashboardViewModel()
ko.applyBindings window.dashboardViewModel