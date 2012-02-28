class Base
  komp: (args...) =>
    ko.computed args...


class Build extends Base
  constructor: (@build_num, @status, @status_title) ->
    @project = ko.observable({})
    @url = @komp =>
      "#{@project().project_path}/#{@build_num}"

    @style = @komp => 'label ' + switch @status
      when "failed"
        "important"
      when "infrastructure_fail"
        "warning"
      when "timedout"
        "important"
      when "no_tests"
        "important"
      when "killed"
        "warning"
      when "fixed"
        "success"
      when "success"
        "success"
      when "running"
        "notice"
      when "starting"
        ""


class Project extends Base
  constructor: (vcs_url, status, latest_build) ->
    @vcs_url = vcs_url
    @status = status
    @latest_build = ko.observable(latest_build)
    @project_name = @vcs_url.substring(19)
    @project_path = "/gh/#{@project_name}"
    @edit_link = "#{@project_path}/edit"



Build::fromJSON = (json) ->
  new Build(json.build_num, json.status, json.status_title)


Project::fromJSON = (json) ->
  build = Build::fromJSON json.latest_build
  p = new Project(json.vcs_url, json.status, build)
  build.project p
  p




class DashboardViewModel extends Base

  constructor: ->
    @projects = ko.observableArray()

    $.getJSON '/api/v1/projects', (data) =>
      for d in data
        @add_json_project(Project::fromJSON d)


  projects_with_status: (filter) => @komp =>
    p for p in @projects() when p.status == filter


  add_json_project: (project) =>
    @projects.push project




window.dashboardViewModel = new DashboardViewModel()
ko.applyBindings window.dashboardViewModel