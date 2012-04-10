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

  komp: (args...) =>
    ko.computed args...


class HasUrl extends Base
  constructor: (json) ->
    super json

    @project_name = @komp =>
      @vcs_url().substring(19)

    @project_path = @komp =>
      "/gh/#{@project_name()}"





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

      else if (@exit_code() == null || @exit_code() == 0)
        "success"
      else
        "failed"

    @success = @komp => (@status() == "success")

    # Expand failing actions
    @minimize(@success())


    @action_header_style = @komp =>
      css = @status()

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
class Build extends HasUrl
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
        @status()

    @committer_mailto = @komp =>
      if @committer_email()
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
      return "(unknown)" unless @branch()

      b = @branch()
      b = b.replace(/^remotes\/origin\//, "")
      "(#{b})"



    @github_url = @komp =>
      return unless @vcs_revision()
      "#{@vcs_url()}/commit/#{@vcs_revision()}"

    @github_revision = @komp =>
      return unless @vcs_revision()
      @vcs_revision().substring 0, 9

    @author = @komp =>
      @committer_name() or @committer_email()

  # TODO: CSRF protection
  retry_build: () =>
    $.post("/api/v1/project/#{@project_path()}/#{@build_num()}/retry")

  description: (include_project) =>
    return unless @build_num?

    if include_project
      "#{@project_name()} ##{@build_num()}"
    else
      @build_num()




class Project extends HasUrl
  constructor: (json) ->
    json.latest_build = (new Build(json.latest_build)) if json.latest_build
    super(json)
    @edit_link = @komp () =>
      "#{@project_path()}/edit"

  checkbox_title: =>
    "Add CI to #{@project_name()}"

  group: =>
    if @status() is 'available'
      "available"
    else
      "followed"

  show_enable_button: =>
    @status() is 'available'

  show_problems: =>
    @status() is 'uninferrable'

  show_options: =>
    @status() is 'followed'

  # We should show this either way, but dont have a good design for it
  show_build: =>
    @status() is 'followed'

  enable: =>
    onerror = (xhr, status, errorThrown) =>
      if errorThrown
        VM.setErrorMessage "HTTP error (#{xhr.status}): #{errorThrown}. Try again? Or contact us."
      else
        VM.setErrorMessage "An unknown error occurred: (#{xhr.status}). Try again? Or contact us."

    onsuccess = (data) =>
      @status(data.status)

    $.post("/api/v1/project/#{@project_name()}/enable")
      .error(onerror)
      .success(onsuccess)






# We use a separate class for Project and ProjectSettings because computed
# observables are calculated eagerly, and that breaks everything if the
class ProjectSettings extends HasUrl
  constructor: (json) ->
    super(json)

    @build_url = @komp =>
      @vcs_url() + '/build'

    @project = @komp =>
      @project_name()

    @has_settings = @komp =>
      full_spec = @setup()
      full_spec += @dependencies()
      full_spec += @test()
      full_spec += @extra()
      "" != full_spec

    @uninferrable = @komp =>
      @status() == "uninferrable"

    @inferred = @komp =>
      (not @uninferrable()) and (not @has_settings())

    @overridden = @komp =>
      (not @uninferrable()) and @has_settings()


  save_hipchat: () =>
    $.ajax(
      type: "PUT"
      url: "/api/v1/project/#{@project_name()}/settings"
      contentType: "application/json"
      data: JSON.stringify(
        hipchat_room: @hipchat_room()
        hipchat_api_token: @hipchat_api_token()
      )
    )
    false # dont bubble the event up

  save_specs: () =>
    $.ajax(
      type: "PUT"
      url: "/api/v1/project/#{@project_name()}/settings"
      contentType: "application/json"
      data: JSON.stringify(
        setup: @setup()
        dependencies: @dependencies()
        test: @test()
        extra: @extra()
      )
    )
    false # dont bubble the event up




class User extends Base
  constructor: (json) ->
    super json,
      admin: false
      login: ""
      is_new: false
      environment: "production"
      basic_email_prefs: "all"

    @showEnvironment = @komp =>
      @admin() || (@environment() is "staging") || (@environment() is "development")

    @environmentColor = @komp =>
      result = {}
      result["env-" + @environment()] = true
      result

  save_preferences: () =>
    $.ajax(
      type: "PUT"
      url: "/api/v1/user/save-preferences"
      contentType: "application/json"
      data: JSON.stringify {basic_email_prefs: @basic_email_prefs()}
    )
    false # dont bubble the event up





display = (template, args) ->
  $('#main').html(HAML[template](args))
  ko.applyBindings(VM)

class CircleViewModel extends Base
  constructor: ->
    @current_user = ko.observable(new User {})
    $.getJSON '/api/v1/me', (data) =>
      @current_user(new User data)

    @build = ko.observable()
    @builds = ko.observableArray()
    @projects = ko.observableArray()
    @recent_builds = ko.observableArray()
    @project_settings = ko.observable()
    @admin = ko.observable()
    @error_message = ko.observable(null)
    @first_login = true;


  clearErrorMessage: () =>
    @error_message null

  setErrorMessage: (message) =>
    @error_message message
    $('html, body').animate({ scrollTop: 0 }, 0);



  loadDashboard: (cx) =>
    $.getJSON '/api/v1/projects', (data) =>
      @projects.removeAll()
      for d in data
        @projects.push(new Project d)
      if @first_login
        @first_login = false
        setTimeout(() => @loadDashboard cx, 3000)


    $.getJSON '/api/v1/recent-builds', (data) =>
      @recent_builds.removeAll()
      for d in data
        @recent_builds.push(new Build d)

    display "dashboard", {}


  loadProject: (cx, username, project) =>
    project_name = "#{username}/#{project}"
    $.getJSON "/api/v1/project/#{project_name}", (data) =>
      @builds.removeAll()
      for d in data
        @builds.push(new Build d)
    display "project", {project: project_name}


  loadBuild: (cx, username, project, build_num) =>
    project_name = "#{username}/#{project}"
    $.getJSON "/api/v1/project/#{project_name}/#{build_num}", (data) =>
      @build(new Build data)
    display "build", {project: project_name, build_num: build_num}


  loadEditPage: (cx, username, project, subpage) =>
    project_name = "#{username}/#{project}"

    # if we're already on this page, dont reload
    if (not @project_settings() or
    (@project_settings().vcs_url() isnt "https://github.com/#{project_name}"))
      $.getJSON "/api/v1/project/#{project_name}/settings", (data) =>
        @project_settings(new ProjectSettings data)

    subpage = subpage[0].replace('#', '')
    subpage = subpage || "settings"
    $('#main').html(HAML['edit']({project: project_name}))
    $('#subpage').html(HAML['edit_' + subpage]())
    ko.applyBindings(VM)


  loadAdminPage: (cx, subpage) =>
    subpage = subpage[0].replace('/', '')
    subpage = subpage || "projects"

    $.getJSON "/api/v1/admin/#{subpage}", (data) =>
      @admin(data)

    $('#main').html(HAML['admin']({}))
    $('#subpage').html(HAML['admin_' + subpage]())
    ko.applyBindings(VM)


  loadAccountPage: (cx) =>
    display "account", {}


  loadJasmineTests: (cx) =>
    # Run the tests within the local scope, so we can use the scope chain to
    # access classes and values throughout this file.
    $.get "/assets/js/tests/inner-tests.dieter", (code) =>
      eval code

  raiseIntercomDialog: () =>
    $("#IntercomDefaultWidget").click();

  logout: (cx) =>
    # TODO: add CSRF protection
    $.post('/logout', () =>
       window.location = "/")

  unsupportedRoute: (cx) =>
    throw("Unsupported route: " + cx.params.splat)

  filtered_projects: (filter) => @komp =>
    p for p in @projects() when p.group() == filter





VM = new CircleViewModel()
stripTrailingSlash = (str) =>
  str.replace(/(.+)\/$/, "$1")

$(document).ready () ->
  Sammy('#app', () ->
    @get('/tests/inner', (cx) -> VM.loadJasmineTests(cx))
    @get('/', (cx) => VM.loadDashboard(cx))
    @get('/gh/:username/:project/edit(.*)', (cx) -> VM.loadEditPage cx, cx.params.username, cx.params.project, cx.params.splat)
    @get('/account', (cx) -> VM.loadAccountPage(cx))
    @get('/gh/:username/:project/:build_num', (cx) -> VM.loadBuild cx, cx.params.username, cx.params.project, cx.params.build_num)
    @get('/gh/:username/:project', (cx) -> VM.loadProject cx, cx.params.username, cx.params.project)
    @get('/logout', (cx) -> VM.logout(cx))
    @get('/admin(.*)', (cx) -> VM.loadAdminPage(cx, cx.params.splat))
    @get('(.*)', (cx) -> VM.unsupportedRoute(cx))
  ).run stripTrailingSlash(window.location.pathname)




# # Events
#   events:
#     "click #reset": "reset_specs"
#     "click #trigger": "trigger_build"
#     "click #trigger_inferred": "trigger_inferred_build"

#   save: (event, btn, redirect, keys) ->
#     event.preventDefault()
#     btn.button 'loading'

#     m.save {},
#       success: ->
#         btn.button 'reset'
#         window.location = redirect
#       error: ->
#         btn.button 'reset'
#         alert "Error in saving project. Please try again. If it persists, please contact Circle."

#   reset_specs: (e) ->
#     @model.set
#       "setup": ""
#       "compile": ""
#       "test": ""
#       "extra": ""
#       "dependencies": ""

#   trigger_build: (e, payload = {}) ->
#     e.preventDefault()
#     btn = $(e.currentTarget)
#     btn.button 'loading'
#     $.post @model.build_url(), payload, () ->
#       btn.button 'reset'

#   trigger_inferred_build: (e) ->
#     @trigger_build e, {inferred: true}
