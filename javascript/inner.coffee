class Base

  project_name: =>
    @vcs_url.substring(19)

  project_path: =>
    "/gh/#{@project_name()}"

  komp: (args...) =>
    ko.computed args...

#TODO: next step is to add the vcs_url, which is why I was looking at the knockout.model and knockout.mapping plugin
class Build extends Base
  constructor: (@vcs_url, @vcs_revision, @build_num, @status, @status_title, \
                @committer_name, @committer_email, @subject, @body) ->

    @url = @komp =>
      "#{@project_path()}/#{@build_num}"

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

  description: (include_project) =>
    return unless @build_num?

    if include_project
      "#{@project_name()} ##{@build_num}"
    else
      @build_num

  github_url: =>
    "#{@vcs_url}/commit/#{@vcs_revision}"

  github_revision: =>
    @vcs_revision.substring 0, 8

  author: =>
    @committer_name or @committer_email









class Project extends Base
  constructor: (vcs_url, status, latest_build) ->
    @vcs_url = vcs_url
    @status = status
    @latest_build = ko.observable(latest_build)
    @edit_link = "#{@project_path()}/edit"





Build::fromJSON = (json) ->
  new Build json.vcs_url, json.vcs_revision, json.build_num, json.status, json.status_title, json.committer_name, json.committer_email, json.subject, json.body




Project::fromJSON = (json) ->
  build = Build::fromJSON json.latest_build
  p = new Project(json.vcs_url, json.status, build)
  p




class DashboardViewModel extends Base

  constructor: ->
    @projects = ko.observableArray()
    @recent_builds = ko.observableArray()

    $.getJSON '/api/v1/projects', (data) =>
      for d in data
        @projects.push(Project::fromJSON d)

    $.getJSON '/api/v1/recent-builds', (data) =>
      for d in data
        @recent_builds.push(Build::fromJSON d)


  projects_with_status: (filter) => @komp =>
    p for p in @projects() when p.status == filter






window.dashboardViewModel = new DashboardViewModel()
ko.applyBindings window.dashboardViewModel