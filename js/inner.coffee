class Base

  constructor: (json, defaults = {}) ->
    for k,v of defaults
      @[k] = @observable(v)

    for k,v of json
      @[k] = @observable(v)

  observable: (obj) ->
    if $.isArray obj
      ko.observableArray obj
    else
      ko.observable obj

  project_name: =>
    @vcs_url().substring(19)

  project_path: =>
    "/gh/#{@project_name()}"

  komp: (args...) =>
    ko.computed args...



class LogOutput extends Base
  constructor: (json) ->
    super json

    @output_style = @komp =>
      out: @type() == "out"
      err: @type() == "err"




class ActionLog extends Base
  constructor: (json) ->
    json.out = (new LogOutput(j) for j in json.out) if json.out
    super json,
      timedout: null
      exit_code: 0
      out: null
      minimize: true

    @status = @komp =>
      if @end_time() == null
        "running"
      else if @timedout()
        "timedout"
      else if @exit_code() == 0
        "success"
      else
        "failed"

    @success = @komp => (@status() == "success")

    # Expand failing actions
    @minimize(@success())


    @action_header_style = @komp =>
      css = @status()
      css = "failed" if css == "timedout"

      result =
        minimize: @minimize()
        contents: @out()

      result[css] = true
      result

    @action_log_style = @komp =>
      minimize: @minimize()

    @duration = @komp () =>
      Circle.time.as_duration(@run_time_millis())

  toggle_minimize: =>
    @minimize(!@minimize())






#TODO: next step is to add the vcs_url, which is why I was looking at the knockout.model and knockout.mapping plugin
class Build extends Base
  constructor: (json) ->
    # make the actionlogs observable
    json.action_logs = (new ActionLog(j) for j in json.action_logs) if json.action_logs
    super json,
      committer_email: null,
      committer_name: null,
      body: null,
      subject: null
      user: null
      branch: null
      start_time: null
      why: null



    @url = @komp =>
      "#{@project_path()}/#{@build_num()}"

    @style = @komp =>
      klass = switch @status()
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
      result = {label: true, build_status: true}
      result[klass] = true
      return result


    @status_words = @komp => switch @status()
      when "infrastructure_fail"
        "circle bug"
      when "timedout"
        "timed out"
      when "no_tests"
        "no tests"
      else
        @status

    @committer_mailto = @komp =>
      "mailto:#{@committer_email()}"

    @why_in_words = @komp =>
      switch @why()
        when "github"
          "GitHub push"
        when "trigger"
          if @user()
            "#{@user()} on CircleCI.com"
          else
            "CircleCI.com"
        else
          if @job_name() == "deploy"
            "deploy"
          else
            "unknown"

    @pretty_start_time = @komp =>
      if @start_time()
        Circle.time.as_time_since(@start_time())

    @duration = @komp () =>
      if @start_time()
        Circle.time.as_duration(@build_time_millis())

    @branch_in_words = @komp =>
      if @branch()
        b = @branch()
        b = b.replace(/^remotes\/origin\//, "")
        "(#{b})"
      else
        "(unknown)"


  description: (include_project) =>
    return unless @build_num?

    if include_project
      "#{@project_name()} ##{@build_num()}"
    else
      @build_num()

  github_url: =>
    "#{@vcs_url()}/commit/#{@vcs_revision()}"

  github_revision: =>
    @vcs_revision().substring 0, 9

  author: =>
    @committer_name() or @committer_email()



class Project extends Base
  constructor: (json) ->
    json.latest_build = (new Build(json.latest_build)) if json.latest_build
    super(json)
    @edit_link = "#{@project_path()}/edit"


class User extends Base
  constructor: (json) ->
    super json


class CircleViewModel extends Base
  constructor: ->
    @current_user = ko.observable()
    $.getJSON '/api/v1/me', (data) =>
      @current_user(new User data)

    @build = ko.observable()
    @builds = ko.observableArray()
    @projects = ko.observableArray()
    @recent_builds = ko.observableArray()

  loadRoot: =>
    @projects.removeAll()
    $.getJSON '/api/v1/projects', (data) =>
      for d in data
        @projects.push(new Project d)

    @recent_builds.removeAll()
    $.getJSON '/api/v1/recent-builds', (data) =>
      for d in data
        @recent_builds.push(new Build d)

    display "dashboard", {}


  loadProject: (username, project) =>
    @builds.removeAll()
    project_name = "#{username}/#{project}"
    $.getJSON "/api/v1/project/#{project_name}", (data) =>
      for d in data
        @builds.push(new Build d)
    display "project", {project: project_name}


  loadBuild: (username, project, build_num) =>
    @build = ko.observable()
    project_name = "#{username}/#{project}"
    $.getJSON "/api/v1/project/#{project_name}/#{build_num}", (data) =>
      @build(new Build data)
    display "build", {project: project_name, build_num: build_num}


  projects_with_status: (filter) => @komp =>
    p for p in @projects() when p.status() == filter


relativeLocation = () ->
  a = document.createElement("a")
  a.href = window.location
  a.pathname

display = (template, args) ->
  $('#main').html(HAML[template](args))
  ko.applyBindings(VM)


VM = new CircleViewModel()


$(document).ready () ->
  Sammy('#app', () ->

    @get('/', (cx) =>
      VM.loadRoot()
    )

    @get('/gh/:username/:project/:build_num', (cx) ->
      VM.loadBuild cx.params.username, cx.params.project, cx.params.build_num
    )

    @get('/gh/:username/:project', (cx) ->
      VM.loadProject cx.params.username, cx.params.project
    )

    @get('/logout', (cx) ->
      # TODO: add CSRF protection
      $.post('/logout', () =>
        window.location = "/"
      )
    )
  ).run(relativeLocation())
