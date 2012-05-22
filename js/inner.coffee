window.observableCount = 0

textVal = (elem, val) ->
  "Takes a jquery element and gets or sets either val() or text(), depending on what's appropriate for this element type (ie input vs button vs a, etc)"
  if elem.is("input")
    if val? then elem.val val else elem.val()
  else # button or a
    if val? then elem.text(val) else elem.text()

finishAjax = (event, attrName, buttonName) ->
  if event
    t = $(event.target)
    done = t.attr(attrName) or buttonName
    textVal t, done

    func = () =>
      textVal t, event.savedText
      t.removeClass "disabled"
    setTimeout(func, 1500)

$(document).ajaxSuccess((ev, xhr, options) -> finishAjax(xhr.event, "data-success-text", "Saved"))

$(document).ajaxError((ev, xhr, status, errorThrown) ->
  finishAjax(xhr.event, "data-failed-text", "Failed")

  if xhr.responseText.indexOf("<!DOCTYPE") is 0
    notifyError "An unknown error occurred: (#{xhr.status} - #{xhr.statusText})."
  else
    notifyError xhr.responseText or xhr.statusText, null, null, true
)

$(document).ajaxSend((ev, xhr, options) ->

  xhr.event = options.event
  if xhr.event
    t = $(xhr.event.target)
    t.addClass "disabled"
    # change to loading text
    loading = t.attr("data-loading-text") or "..."
    xhr.event.savedText = textVal t
    textVal t, loading
)


# Make the buttons disabled when clicked
$.ajaxSetup
  contentType: "application/json"
  accepts: {json: "application/json"}
  dataType: "json"


class Base
  constructor: (json, defaults={}, nonObservables=[], observe=true) ->
    for k,v of defaults
      if observe and nonObservables.indexOf(k) == -1
        @[k] = @observable(v)
      else
        @[k] = v

    for k,v of json
      if observe and nonObservables.indexOf(k) == -1
        @[k] = @observable(v)
      else
        @[k] = v

  observable: (obj) ->
    observableCount += 1
    if $.isArray obj
      ko.observableArray obj
    else
      ko.observable obj

  komp: (args...) =>
    observableCount += 1
    ko.computed args...


class HasUrl extends Base
  constructor: (json, defaults, nonObservables) ->
    super json, defaults, nonObservables

    @project_name = @komp =>
      @vcs_url().substring(19)

    @project_path = @komp =>
      "/gh/#{@project_name()}"



class ActionLog extends Base
  constructor: (json) ->
    super json, {bash_command: null, start_time: null, command: null, timedout: null, exit_code: 0, out: null, minimize: true}, ["end_time", "timedout", "exit_code", "run_time_millis", "out", "start_time", "bash_command"]

    @status = if @end_time == null
        "running"
      else if @timedout
        "timedout"

      else if (@exit_code == null || @exit_code == 0)
        "success"
      else
        "failed"

    @success = @status == "success"

    # Expand failing actions
    @minimize(@success)

    @has_content = () =>
      @out or @bash_command

    @action_header_style = @komp =>
      css = @status

      result =
        minimize: @minimize()
        contents: @has_content()
      result[css] = true
      result

    @action_log_style =
      minimize: @minimize()

    @start_to_end_string = "#{@start_time} to #{@end_time}"

    @duration = Circle.time.as_duration(@run_time_millis)

  toggle_minimize: =>
    if @has_content()
      @minimize(!@minimize())


  htmlEscape: (str) =>
    str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  log_output: =>
    return "" unless @out
    x = for o in @out
      "<span class='#{o.type}'>#{@htmlEscape(o.message)}</span>"
    x.join ""





#TODO: next step is to add the vcs_url, which is why I was looking at the knockout.model and knockout.mapping plugin
class Build extends HasUrl
  constructor: (json) ->
    # make the actionlogs observable
    json.action_logs = (new ActionLog(j) for j in json.action_logs) if json.action_logs
    super json, {}, ["build_num", "status", "committer_name", "committer_email", "why", "user", "job_name", "branch", "vcs_revision", "start_time", "build_time_millis"]


    @url = @komp =>
      "#{@project_path()}/#{@build_num}"

    @style = @komp =>
      klass = switch @status
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
        when "deploy"
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


    @status_words = @komp => switch @status
      when "infrastructure_fail"
        "circle bug"
      when "timedout"
        "timed out"
      when "no_tests"
        "no tests"
      when "not_running"
        "not running"
      else
        @status

    @committer_mailto = @komp =>
      if @committer_email
        "mailto:#{@committer_email}"

    @why_in_words = @komp =>
      switch @why
        when "github"
          "GitHub push"
        when "trigger"
          if @user
            "#{@user} on CircleCI.com"
          else
            "CircleCI.com"
        else
          if @job_name == "deploy"
            "deploy"
          else
            "unknown"

    @pretty_start_time = @komp =>
      if @start_time
        Circle.time.as_time_since(@start_time)

    @duration = @komp () =>
      if @start_time
        Circle.time.as_duration(@build_time_millis)

    @branch_in_words = @komp =>
      return "(unknown)" unless @branch

      b = @branch
      b = b.replace(/^remotes\/origin\//, "")
      "(#{b})"



    @github_url = @komp =>
      return unless @vcs_revision
      "#{@vcs_url()}/commit/#{@vcs_revision}"

    @github_revision = @komp =>
      return unless @vcs_revision
      @vcs_revision.substring 0, 9

    @author = @komp =>
      @committer_name or @committer_email

  # TODO: CSRF protection
  retry_build: (data, event) =>
    $.ajax(
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/retry"
      type: "POST"
      event: event
    )
    false

  report_build: () =>
    VM.raiseIntercomDialog('I think I found a bug in Circle at ' + window.location + '\n\n')


  description: (include_project) =>
    return unless @build_num?

    if include_project
      "#{@project_name()} ##{@build_num}"
    else
      @build_num




class Project extends HasUrl
  constructor: (json) ->
    json.latest_build = (new Build(json.latest_build)) if json.latest_build
    super json
    @edit_link = @komp () =>
      "#{@project_path()}/edit"

    @build_url = @komp =>
      @vcs_url() + '/build'

    @has_settings = @komp =>
      @setup() or @dependencies() or @test() or @extra()

    @uninferrable = @komp =>
      @status() == "uninferrable"

    @inferred = @komp =>
      (not @uninferrable()) and (not @has_settings())

    @overridden = @komp =>
      (not @uninferrable()) and @has_settings()




  @sidebarSort: (l, r) ->
    if l.latest_build()
      if r.latest_build()
        if l.latest_build().build_num < r.latest_build().build_num then 1 else -1
      else
        -1
    else
      if l.vcs_url().toLowerCase() > r.vcs_url().toLowerCase() then 1 else -1


  checkbox_title: =>
    "Add CI to #{@project_name()}"

  uninferrable: =>
    @status() is 'uninferrable'

  unfollow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/unfollow"
      success: (data) =>
        @status(data.status)
        @followed(data.followed)

  follow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/follow"
      success: (data) =>
        # The new model here is not going to be "enabled" and "available", but
        # will allow you to add a project without being an admin
        @status(data.status)
        @followed(data.followed)

  save_hipchat: (data, event) =>
    $.ajax(
      type: "PUT"
      event: event
      url: "/api/v1/project/#{@project_name()}/settings"
      data: JSON.stringify(
        hipchat_room: @hipchat_room()
        hipchat_api_token: @hipchat_api_token()
      )
    )
    false # dont bubble the event up

  save_specs: (data, event) =>
    $.ajax(
      type: "PUT"
      event: event
      url: "/api/v1/project/#{@project_name()}/settings"
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
    json.tokens = ko.observableArray(json.tokens or [])
    json.paid = ko.observable(json.paid or false)
    json.plan = ko.observable(json.plan)
    json.environment = window.renderContext.env if window.renderContext
    super json, {paid: false, admin: false, login: "", basic_email_prefs: "all", card_on_file: false, plan: null, environment: "production"}, [], false

    @tokenLabel = ko.observable("")

    @showEnvironment = @komp =>
      @admin || (@environment is "staging") || (@environment is "development")

    @environmentColor = @komp =>
      result = {}
      result["env-" + @environment] = true
      result

    # billing
    @plans = ko.observable()
    @individualPlan = @komp =>
      if @plan()? and @plans()?
        @plans()[@plan()]




    @availablePlans = @komp =>
      ap = for k,v of @plans()
        v.id = k
        v
      ap.sort (a, b) ->
        b.price - a.price # most expensive first
      ap

    # team billing
    @collaborators = ko.observable(null)
    @showTeam = ko.observable(false)

    @individualTotal = @komp =>
      if @individualPlan() then @individualPlan().price / 100 else 0

    @teamTotal = @komp =>
      return 0 unless @collaborators()
      total = 0
      for c in @collaborators()
        total += c.plan().price if c.plan()
      total / 100

    @overallTotal = @komp =>
      @teamTotal() + @individualTotal()


  # billing
  selectPlan: (data) =>
    @plan(data.id)

  toggleTeam: () =>
    @showTeam !@showTeam()

  teamPlans: () =>
    return {} unless @collaborators()
    result = {}
    for c in @collaborators()
      result[c.login] = c.plan().plan if c.plan()
    result





  stripeSubmit: (data, event) ->
    key = switch @environment
      when "production" then "pk_ZPBtv9wYtkUh6YwhwKRqL0ygAb0Q9"
      else 'pk_Np1Nz5bG0uEp7iYeiDIElOXBBTmtD'
    Stripe.setPublishableKey(key)

    # disable the submit button to prevent repeated clicks
    button = $('.submit-button')
    button.addClass "disabled"

    Stripe.createToken {
      number: $('.card-number').val()
      cvc: $('.card-cvc').val(),
      exp_month: $('.card-expiry-month').val(),
      exp_year: $('.card-expiry-year').val()
    }, (status, response) =>
      if response.error
        button.removeClass "disabled"
        notifyError response.error.message
      else
        @recordStripeTransaction event, response # TODO: add the plan


    # prevent the form from submitting with the default action
    return false;

  recordStripeTransaction: (event, response) =>
    $.ajax(
      url: "/api/v1/user/pay"
      event: event
      type: "POST"
      data: JSON.stringify
        token: response
        plan: @individualPlan().id
        team_plans: @teamPlans()
      success: () =>
        @paid true
    )
    false

  create_token: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/user/create-token"
      data: JSON.stringify {label: @tokenLabel()}
      success: (result) =>
        @tokens result
        @tokenLabel("")
        true
    false


  save_preferences: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/user/save-preferences"
      data: JSON.stringify {basic_email_prefs: @basic_email_prefs}
    false # dont bubble the event up





display = (template, args) ->
  $('#main').html(HAML[template](args))
  ko.applyBindings(VM)

class CircleViewModel extends Base
  constructor: ->
    observableCount = 0
    @current_user = ko.observable(new User window.renderContext.current_user)
    @build = ko.observable()
    @builds = ko.observableArray()
    @project = ko.observable()
    @projects = ko.observableArray()
    @recent_builds = ko.observableArray()
    @build_state = ko.observable()
    @admin = ko.observable()
    @error_message = ko.observable(null)
    @first_login = true;
    @refreshing_projects = ko.observable(false);
    @project_map = {}
    observableCount += 8

    #@setupPusher()

  setupPusher: () =>
    @pusher = new Pusher("356b7c379e56e14c261b")

    Pusher.channel_auth_endpoint = "/auth/pusher"

    @userSubscribePrivateChannel()
    @pusherSetupBindings()

  userSubscribePrivateChannel: () =>
    channel_name = "private-" + @current_user().login

    @user_channel = @pusher.subscribe(channel_name)
    @user_channel.bind('pusher:subscription_error', (status) -> notifyError status)

  pusherSetupBindings: () =>
    @user_channel.bind "call", (data) =>
      window.fn = data.fn
      window.args = data.args
      this[data.fn].apply(this, data.args)

  testCall: (arg) =>
    alert(arg)

  clearErrorMessage: () =>
    @error_message null

  setErrorMessage: (message) =>
    if message == ""
      message = "Unknown error"
    if message.slice(-1) != '.'
      message += '.'
    @error_message message
    $('html, body').animate({ scrollTop: 0 }, 0);

  loadProjects: () =>
    $.getJSON '/api/v1/projects', (data) =>
      start_time = Date.now()
      projects = (new Project d for d in data)
      projects.sort Project.sidebarSort
      for p in projects
        @project_map[p.vcs_url()] = p

      @projects(projects)
      window.time_taken_projects = Date.now() - start_time
      if @first_login
        @first_login = false
        setTimeout(@loadProjects, 3000)

  available_projects: () => @komp =>
    (p for p in @projects() when not p.followed())

  followed_projects: () => @komp =>
    (p for p in @projects() when p.followed())

  user_refresh_projects: (data, event) =>
    @refreshing_projects true
    $.ajax
      url: "/api/v1/user/project-refresh"
      type: "POST"
      event: event
      complete:
        (jqXHR, text_status) =>
          @refreshing_projects false
      success:
        (data) =>
          @loadProjects()

  refresh_project_src: () => @komp =>
    if @refreshing_projects()
      "/img/ajax-loader.gif"
    else
      "/img/arrow_refresh.png"

  loadRecentBuilds: () =>
    $.getJSON '/api/v1/recent-builds', (data) =>
      start_time = Date.now()
      @recent_builds((new Build d for d in data))
      window.time_taken_recent_builds = Date.now() - start_time


  loadDashboard: (cx) =>
    @loadProjects()
    @loadRecentBuilds()
    display "dashboard", {}


  loadProject: (cx, username, project) =>
    project_name = "#{username}/#{project}"
    $.getJSON "/api/v1/project/#{project_name}", (data) =>
      start_time = Date.now()
      @builds((new Build d for d in data))
      window.time_taken_project = Date.now() - start_time
    display "project", {project: project_name}


  loadBuild: (cx, username, project, build_num) =>
    project_name = "#{username}/#{project}"
    $.getJSON "/api/v1/project/#{project_name}/#{build_num}", (data) =>
      start_time = Date.now()
      @build(new Build data)
      window.time_taken_build = Date.now() - start_time
    display "build", {project: project_name, build_num: build_num}


  loadEditPage: (cx, username, project, subpage) =>
    project_name = "#{username}/#{project}"

    # if we're already on this page, dont reload
    if (not @project() or
    (@project().vcs_url() isnt "https://github.com/#{project_name}"))
      $.getJSON "/api/v1/project/#{project_name}/settings", (data) =>
        @project(new Project data)

    subpage = subpage[0].replace('#', '')
    subpage = subpage || "settings"
    $('#main').html(HAML['edit']({project: project_name}))
    $('#subpage').html(HAML['edit_' + subpage]({}))
    ko.applyBindings(VM)


  loadStripe: () =>
    unless @stripeLoaded
      @stripeLoaded = true
      s = document.createElement 'script'
      s.setAttribute "type", "text/javascript"
      s.setAttribute "src", "https://js.stripe.com/v1/"
      document.getElementsByTagName("head")[0].appendChild(s)

  loadTeamMembers: () =>
    $.getJSON '/api/v1/user/team-members', (data) =>
      for d in data
        d.plan = ko.observable(null)
      @current_user().collaborators(data)

  loadPlans: () =>
    $.getJSON '/api/v1/user/plans', (data) =>
      @current_user().plans(data)


  loadAccountPage: (cx, subpage) =>
    subpage = subpage[0].replace('/', '')
    subpage = subpage || "notifications"
    if subpage == 'plans'
      @loadPlans()
      @loadTeamMembers()
      @loadStripe()

    $('#main').html(HAML['account']({}))
    $('#subpage').html(HAML['account_' + subpage.replace('-', '_')]({}))
    ko.applyBindings(VM)
    $("##{subpage}").addClass('active')


  renderAdminPage: (subpage) =>
    $('#main').html(HAML['admin']({}))
    if subpage
      $('#subpage').html(HAML['admin_' + subpage]())
    ko.applyBindings(VM)


  loadAdminPage: (cx, subpage) =>
    if subpage
      subpage = subpage.replace('/', '')
      $.getJSON "/api/v1/admin/#{subpage}", (data) =>
        @admin(data)
    @renderAdminPage subpage

  loadAdminBuildState: () =>
    $.getJSON '/api/v1/admin/build-state', (data) =>
      @build_state(data)
    @renderAdminPage "build_state"


  loadAdminProjects: (cx) =>
    $.getJSON '/api/v1/admin/projects', (data) =>
      data = (new Project d for d in data)
      @projects(data)
    @renderAdminPage "projects"


  loadAdminRecentBuilds: () =>
    $.getJSON '/api/v1/admin/recent-builds', (data) =>
      @recent_builds((new Build d for d in data))
    @renderAdminPage "recent_builds"

  adminRefreshIntercomData: (data, event) =>
    $.ajax(
      url: "/api/v1/admin/refresh-intercom-data"
      type: "POST"
      event: event
    )
    false


  loadJasmineTests: (cx) =>
    # Run the tests within the local scope, so we can use the scope chain to
    # access classes and values throughout this file.
    $.get "/assets/js/tests/inner-tests.dieter", (code) =>
      eval code

  raiseIntercomDialog: (message=null) =>
    jq = intercomJQuery
    jq("#IntercomDefaultWidget").click()
    unless jq('#IntercomNewMessageContainer').is(':visible')
      jq('.new_message').click()
    jq('#newMessageBody').focus()
    if message
      jq('#newMessageBody').text(message)

  logout: (cx) =>
    # TODO: add CSRF protection
    $.post('/logout', () =>
       window.location = "/")

  unsupportedRoute: (cx) =>
    throw("Unsupported route: " + cx.params.splat)






window.VM = new CircleViewModel()
stripTrailingSlash = (str) =>
  str.replace(/(.+)\/$/, "$1")

$(document).ready () ->
  Sammy('#app', () ->
    @get('/tests/inner', (cx) -> VM.loadJasmineTests(cx))

    @get('/', (cx) => VM.loadDashboard(cx))
    @get('/gh/:username/:project/edit(.*)',
      (cx) -> VM.loadEditPage cx, cx.params.username, cx.params.project, cx.params.splat)
    @get('/account(.*)',
      (cx) -> VM.loadAccountPage(cx, cx.params.splat))
    @get('/gh/:username/:project/:build_num',
      (cx) -> VM.loadBuild cx, cx.params.username, cx.params.project, cx.params.build_num)
    @get('/gh/:username/:project',
      (cx) -> VM.loadProject cx, cx.params.username, cx.params.project)

    @get('/logout', (cx) -> VM.logout(cx))

    @get('/admin', (cx) -> VM.loadAdminPage cx)
    @get('/admin/users', (cx) -> VM.loadAdminPage cx, "users")
    @get('/admin/projects', (cx) -> VM.loadAdminProjects cx)
    @get('/admin/recent-builds', (cx) -> VM.loadAdminRecentBuilds cx)
    @get('/admin/build-state', (cx) -> VM.loadAdminBuildState cx)

    @get('(.*)', (cx) -> VM.unsupportedRoute(cx))

    # Google analytics
    @bind 'event-context-after', ->
      if window._gaq? # we dont use ga in test mode
        window._gaq.push @path


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
