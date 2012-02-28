class Project
  constructor: (vcs_url, status) ->
    @vcs_url = vcs_url
    @status = status
    @project_name = @komputed () => @vcs_url.substring(19)
    @project_path = @komputed () => '/gh/' + @project_name()
    @edit_link = @komputed () => @project_path() + '/edit'

  # the @ causes very awkward code if used directly
  komputed: (callback) =>
    ko.computed(callback, @)

   latest_build: () =>
    @komputed () => "z"



class DashboardViewModel

  constructor: () ->
    @projects = ko.observableArray()

    $.getJSON '/api/v1/projects', (data) =>
      for d in data
        @addProject(d.vcs_url, d.status, d.project_url)


  projects_with_status: (filter) =>
    ko.computed(
      () => (p for p in @projects() when p.status == filter)
      this)


  addProject: (vcs_url, status) =>
    @projects.push(new Project(vcs_url, status))



window.dashboardViewModel = new DashboardViewModel()
ko.applyBindings window.dashboardViewModel