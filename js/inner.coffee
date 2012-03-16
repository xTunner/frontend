class Base

  constructor: (json) ->
    for k,v of json
      @[k] = ko.observable(v)

  project_name: =>
    @vcs_url().substring(19)

  project_path: =>
    "/gh/#{@project_name()}"

  komp: (args...) =>
    ko.computed args...

#TODO: next step is to add the vcs_url, which is why I was looking at the knockout.model and knockout.mapping plugin
class Build extends Base
  constructor: (json) ->
    super(json)

    @url = @komp =>
      "#{@project_path()}/#{@build_num()}"

    @style = @komp => 'label ' + switch @status()
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
    @status_words = @komp => switch @status()
      when "infrastructure_fail"
        "infrastructure fail"
      when "timedout"
        "timed out"
      else
        @status


  description: (include_project) =>
    return unless @build_num?

    if include_project
      "#{@project_name()} ##{@build_num()}"
    else
      @build_num()

  github_url: =>
    "#{@vcs_url()}/commit/#{@vcs_revision()}"

  github_revision: =>
    @vcs_revision().substring 0, 8

  author: =>
    @committer_name() or @committer_email()


class Project extends Base
  constructor: (json) ->
    super(json)
    @edit_link = "#{@project_path()}/edit"


class User extends Base
  constructor: (json) ->
    super json


class DashboardViewModel extends Base

  constructor: ->
    @projects = ko.observableArray()
    @recent_builds = ko.observableArray()
    @current_user = ko.observable()

    $.getJSON '/api/v1/projects', (data) =>
      for d in data
        @projects.push(new Project d)

    $.getJSON '/api/v1/recent-builds', (data) =>
      for d in data
        @recent_builds.push(new Build d)

    $.getJSON '/api/v1/me', (data) =>
      @current_user(new User data)


  projects_with_status: (filter) => @komp =>
    p for p in @projects() when p.status() == filter


relativeLocation = () ->
  a = document.createElement("a")
  a.href = window.location
  a.pathname

$(document).ready () ->
  Sammy('body', () ->

    @get('/', (cx) =>
      $('body').html(HAML['dashboard']({}))
      ko.applyBindings(new DashboardViewModel()))

    @get('/gh/:username/:project', (cx) ->
      $('body').html(HAML['dashboard']({})))

    @get('/logout', (cx) ->
      # TODO: add CSRF protection
      $.post('/logout', () =>
        window.location = "/")
      )

  ).run(relativeLocation())
