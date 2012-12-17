window.observableCount = 0

log2 = (v) ->
  Math.log(v) / Math.log(2)

textVal = (elem, val) ->
  "Takes a jquery element and gets or sets either val() or text(), depending on what's appropriate for this element type (ie input vs button vs a, etc)"
  if elem.is("input")
    if val? then elem.val val else elem.val()
  else # button or a
    if val? then elem.text(val) else elem.text()

finishAjax = (event, attrName, buttonName) ->
  if event
    t = $(event.currentTarget)
    done = t.attr(attrName) or buttonName
    textVal t, done

    func = () =>
      textVal t, event.savedText
      t.removeClass "disabled"
    setTimeout(func, 1500)

$(document).ajaxSuccess (ev, xhr, options) ->
  finishAjax(xhr.event, "data-success-text", "Saved")

$(document).ajaxError (ev, xhr, settings, errorThrown) ->
  finishAjax(xhr.event, "data-failed-text", "Failed")
  if xhr.status == 401
    notifyError "You've been logged out, log back in to continue."
  else if xhr.responseText.indexOf("<!DOCTYPE") is 0
    notifyError "An unknown error occurred: (#{xhr.status} - #{xhr.statusText})."
  else
    notifyError (xhr.responseText or xhr.statusText)

$(document).ajaxSend (ev, xhr, options) ->
  xhr.event = options.event
  if xhr.event
    t = $(xhr.event.currentTarget)
    t.addClass "disabled"
    # change to loading text
    loading = t.attr("data-loading-text") or "..."
    xhr.event.savedText = textVal t
    textVal t, loading


# Make the buttons disabled when clicked
$.ajaxSetup
  contentType: "application/json"
  accepts: {json: "application/json"}
  dataType: "json"

ko.observableArray["fn"].setIndex = (index, newItem) ->
  @valueWillMutate()
  result = @()[index] = newItem
  @valueHasMutated()
  result

komp = (args...) =>
  observableCount += 1
  ko.computed args...

ansiToHtml = (str) ->
  # http://en.wikipedia.org/wiki/ANSI_escape_code
  start   = 0
  current = str
  output  = ""

  style =
    color: null
    italic: false
    bold: false

    reset: () ->
      @color = null
      @italic = false
      @bold = false

    add: (n) ->
      switch parseInt(n)
        when 0 then @reset()
        when 1 then @bold = true
        when 3 then @italic = true
        when 22 then @bold = false
        when 23 then @italic = false
        when 30 then @color = "black"
        when 31 then @color = "red"
        when 32 then @color = "green"
        when 33 then @color = "yellow"
        when 34 then @color = "blue"
        when 35 then @color = "magenta"
        when 36 then @color = "cyan"
        when 37 then @color = "white"
        when 39 then @color = null

    openSpan: () ->
      styles = []
      if @color?
        styles.push("color: #{@color}")
      if @italic
        styles.push("font-style: italic")
      if @bold
        styles.push("font-weight: bold")

      s = "<span"
      if styles.length > 0
        s += " style='" + styles.join("; ") + "'"
      s += ">"

    applyTo: (content) ->
      if content
        @openSpan() + content + "</span>"
      else
        ""

  # loop over escape sequences
  while (escape_start = current.indexOf('\u001B[')) != -1
    # append everything up to the start of the escape sequence to the output
    output += style.applyTo(current.slice(0, escape_start))

    # find the end of the escape sequence -- a single letter
    rest = current.slice(escape_start + 2)
    escape_end = rest.search(/[A-Za-z]/)

    # point "current" at first character after the end of the escape sequence
    current = rest.slice(escape_end + 1)

    # only actually deal with 'm' escapes
    if rest.charAt(escape_end) == 'm'
      escape_sequence = rest.slice(0, escape_end)
      if escape_sequence == ''
        # \esc[m is equivalent to \esc[0m
        style.reset()
      else
        escape_codes = escape_sequence.split(';')
        style.add esc for esc in escape_codes

  output += style.applyTo(current)
  output

class Obj
  constructor: (json={}, defaults={}) ->
    for k,v of @observables()
      @[k] = @observable(v)

    for k,v of $.extend {}, defaults, json
      if @observables().hasOwnProperty(k) then @[k](v) else @[k] = v

  observables: () => {}

  observable: (obj) ->
    observableCount += 1
    if $.isArray obj
      ko.observableArray obj
    else
      ko.observable obj

  updateObservables: (obj) =>
    for k,v of obj
      if @observables().hasOwnProperty(k)
        @[k](v)

VcsUrlMixin = (obj) ->
  obj.vcs_url = ko.observable(if obj.vcs_url then obj.vcs_url else "")

  obj.observables.vcs_url = obj.vcs_url

  obj.project_name = komp ->
    obj.vcs_url().substring(19)

  obj.project_display_name = komp ->
    obj.project_name().replace("/", '/\u200b')

  obj.project_path = komp ->
    "/gh/#{obj.project_name()}"

  obj.edit_link = komp () =>
    "#{obj.project_path()}/edit"



## Deprecated. Do not use for new classes.
class Base extends Obj
  constructor: (json, defaults={}, nonObservables=[], observe=true) ->
    for k,v of $.extend {}, defaults, json
      if observe and nonObservables.indexOf(k) == -1
        @[k] = @observable(v)
      else
        @[k] = v

class ActionLog extends Obj
  observables: =>
    name: null
    bash_command: null
    timedout: null
    start_time: null
    end_time: null
    exit_code: null
    run_time_millis: null
    status: null
    source: null
    type: null
    out: []
    user_minimized: null # tracks whether the user explicitly minimized. nil means they haven't touched it

  constructor: (json) ->
    super json

    @success = komp =>
      @status() == "success"

    @failed = komp => @status() == "failed" or @status() == "timedout"
    @infrastructure_fail = komp => @status() == "infrastructure_fail"

    # Expand failing actions
    @minimize = komp =>
      if @user_minimized()?
        @user_minimized()
      else
        @success()

    @visible = komp =>
      not @minimize()

    @has_content = komp =>
      (@out()? and @out().length > 0) or @bash_command()

    @action_header_style =
      # knockout CSS requires a boolean observable for each of these
      minimize: @minimize
      contents: @has_content
      running: komp => @status() == "running"
      timedout: komp => @status() == "timedout"
      success: komp => @status() == "success"
      failed: komp => @status() == "failed"

    @action_header_button_style = komp =>
      if @has_content()
        @action_header_style
      else
        {}

    @action_log_style =
      minimize: @minimize

    @start_to_end_string = komp =>
      "#{@start_time()} to #{@end_time()}"

    @duration = komp =>
      Circle.time.as_duration(@run_time_millis())

    @sourceText = komp =>
      @source()

    @sourceTitle = komp =>
      switch @source()
        when "template"
          "Circle generated this command automatically"
        when "cache"
          "Circle caches some subdirectories to significantly speed up your tests"
        when "config"
          "You specified this command in your circle.yml file"
        when "inference"
          "Circle inferred this command from your source code and directory layout"
        when "db"
          "You specified this command on the project settings page"

  toggle_minimize: =>
    if not @user_minimized?
      @user_minimized(!@user_minimized())
    else
      @user_minimized(!@minimize())

  htmlEscape: (str) =>
    str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  log_output: =>
    return "" unless @out()
    x = for o in @out()
      "<p class='#{o.type}'><span class='bubble'></span>#{ansiToHtml(@htmlEscape(o.message))}</p>"
    x.join ""

  report_build: () =>
    VM.raiseIntercomDialog('I think I found a bug in Circle at ' + window.location + '\n\n')

class Step extends Obj

  observables: =>
    actions: []

  constructor: (json) ->
    json.actions = if json.actions? then (new ActionLog(j) for j in json.actions) else []
    super json

class Build extends Obj
  observables: =>
    messages: []
    build_time_millis: null
    committer_name: null
    committer_email: null
    committer_date: null
    author_name: null
    author_email: null
    author_date: null
    body: null
    start_time: null
    stop_time: null
    steps: []
    status: null
    lifecycle: null
    outcome: null
    failed: null
    infrastructure_fail: null
    dont_build: null
    name: null
    branch: "unknown"
    previous: null
    retry_of: null
    subject: null

  constructor: (json) ->

    json.steps = if json.steps? then (new Step(j) for j in json.steps) else []

    super(json)

    VcsUrlMixin(@)

    @url = komp =>
      @urlForBuildNum @build_num

    @important_style = komp =>
      switch @status()
        when "failed"
          true
        when "timedout"
          true
        when "no_tests"
          true
        else
          false

    @warning_style = komp =>
      switch @status()
        when "infrastructure_fail"
          true
        when "killed"
          true
        when "not_run"
          true
        else
          false

    @success_style = komp =>
      switch @outcome()
        when "success"
          true
        else
          false

    @info_style = komp =>
      switch @status()
        when "running"
          true
        else
          false

    @style =
      "label-important": @important_style
      "label-warning": @warning_style

      "label-success": @success_style
      "label-info": @info_style
      label: true
      build_status: true

    @status_words = komp => switch @status()
      when "infrastructure_fail"
        "circle bug"
      when "timedout"
        "timed out"
      when "no_tests"
        "no tests"
      when "not_run"
        "not run"
      when "not_running"
        "not running"
      else
        @status()

    @why_in_words = komp =>
      switch @why
        when "github"
          "GitHub push by #{@user.login}"
        when "edit"
          "Edit of the project settings"
        when "first-build"
          "First build"
        when "retry"
          "Manual retry of build #{@retry_of()}"
        when "ssh"
          "Retry of build #{@retry_of()}, with SSH enabled"
        when "auto-retry"
          "Auto-retry of build #{@retry_of()}"
        when "trigger"
          if @user
            "#{@user} on CircleCI.com"
          else
            "CircleCI.com"
        else
          if @job_name?
            @job_name
          else
            "unknown"

    @can_cancel = komp =>
      if @status() == "canceled"
        false
      else
        switch @lifecycle()
          when "running"
            true
          when "queued"
            true
          when "scheduled"
            true
          else
            false

    @pretty_start_time = komp =>
      if @start_time()
        Circle.time.as_time_since(@start_time())

    @previous_build = komp =>
      @previous()? and @previous().build_num

    @duration = komp () =>
      if @build_time_millis()?
        Circle.time.as_duration(@build_time_millis())
      else
        "still running"

    @branch_in_words = komp =>
      return "(unknown)" unless @branch()

      b = @branch()
      b = b.replace(/^remotes\/origin\//, "")
      "(#{b})"

    @github_url = komp =>
      return unless @vcs_revision
      "#{@vcs_url()}/commit/#{@vcs_revision}"

    @github_revision = komp =>
      return unless @vcs_revision
      @vcs_revision.substring 0, 7

    @author = komp =>
      @author_name() or @author_email()

    @committer = komp =>
      @committer_name() or @committer_email()

    @committer_mailto = komp =>
      if @committer_email()
        "mailto:#{@committer_email}"

    @author_mailto = komp =>
      if @committer_email()
        "mailto:#{@committer_email()}"

    @author_isnt_committer = komp =>
      (@committer_email() isnt @author_email()) or (@committer_name() isnt @author_name())


   # hack - how can an action know its type is different from the previous, when
   # it doesn't even have access to the build
  different_type: (action) =>
    last = null
    breakLoop = false
    for s in @steps()
      for a in s.actions()
        if a == action
          breakLoop = true # no nested breaks in CS
          break
        last = a
      if breakLoop
        break

    last? and not (last.type() == action.type())

  urlForBuildNum: (num) =>
    "#{@project_path()}/#{num}"

  invite_user: (data, event) =>
    $.ajax
      url: "/api/v1/account/invite"
      type: "POST"
      event: event
      data: JSON.stringify
        invitee: @user
        vcs_url: @vcs_url()
        build_num: @build_num
    event.stopPropagation()


  visit: () =>
    SammyApp.setLocation @url()

  isRunning: () =>
    @start_time() and not @stop_time()

  shouldSubscribe: () =>
    @isRunning() or @status() == "queued" or @status() == "scheduled"

  maybeSubscribe: () =>
    if @shouldSubscribe()
      @build_channel = VM.pusher.subscribe(@pusherChannel())
      @build_channel.bind('pusher:subscription_error', (status) -> notifyError status)

      @build_channel.bind('newAction', @newAction)
      @build_channel.bind('updateAction', @updateAction)
      @build_channel.bind('appendAction', @appendAction)
      @build_channel.bind('updateObservables', @updateObservables)

  fillActions: (step, index) =>
    # fills up steps and actions such that step and index are valid
    for i in [0..step]
      if not @steps()[i]?
        @steps.setIndex(i, new Step({}))

    # actions can arrive out of order when doing parallel. Fill up the other indices so knockout doesn't bitch
    for i in [0..index]
      if not @steps()[step].actions()[i]?
        @steps()[step].actions.setIndex(i, new ActionLog({}))

  newAction: (json) =>
    @fillActions(json.step, json.index)
    @steps()[json.step].actions.setIndex(json.index, new ActionLog(json.log))

  updateAction: (json) =>
    # updates the observables on the action, such as end time and status.
    @fillActions(json.step, json.index)
    @steps()[json.step].actions()[json.index].updateObservables(json.log)

  appendAction: (json) =>
    # adds output to the action
    @fillActions(json.step, json.index)

    # @steps()[json.step].actions()[json.index].out.push(json.out)
    out = @steps()[json.step].actions()[json.index].out
    len = out().length
    last = out()[len - 1]
    payload = json.out
    if last? and last.type == payload.type
      out.valueWillMutate()
      last.message += payload.message
      out.valueHasMutated()
    else
      out.push(payload)

  # TODO: CSRF protection
  retry_build: (data, event) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/retry"
      type: "POST"
      event: event
      success: (data) =>
        (new Build(data)).visit()
    false

  ssh_build: (data, event) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/ssh"
      type: "POST"
      event: event
      success: (data) =>
        (new Build(data)).visit()
    false

  cancel_build: (data, event) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/cancel"
      type: "POST"
      event: event
    false

  report_build: () =>
    VM.raiseIntercomDialog('I think I found a bug in Circle at ' + window.location + '\n\n')

  description: (include_project) =>
    return unless @build_num?

    if include_project
      "#{@project_name()} ##{@build_num}"
    else
      @build_num

  pusherChannel: () =>
    "private-#{@project_name()}@#{@build_num}".replace(/\//g,"@")

  update: (json) =>
    @status(json.status)

class Repo extends Obj
  ## A Repo comes from github, may or may not be in the DB yet

  observables: =>
    following: false

  constructor: (json) ->
    super json
    VcsUrlMixin(@)

    @canFollow = komp =>
      not @following() and (@admin or @has_followers)

    @requiresInvite = komp =>
      not @following() and not @admin and not @has_followers

    @displayName = komp =>
      if @fork
        @project_name()
      else
        @name



  unfollow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/unfollow"
      success: (data) =>
        @following(false)
        _kmq.push(['record', 'Removed A Repo']);
        _gaq.push(['_trackEvent', 'Repos', 'Remove']);

  follow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/follow"
      success: (data) =>
        _kmq.push(['record', 'Added A Repo']);
        _gaq.push(['_trackEvent', 'Repos', 'Add']);
        if @first_login
          _kmq.push(['record', 'Added A Project on First Login']);
          _gaq.push(['_trackEvent', 'Repos', 'Added on First Login']);
        @following(true)
        if data.first_build
          (new Build(data.first_build)).visit()
        else
          $('html, body').animate({ scrollTop: 0 }, 0);
          VM.loadRecentBuilds()

class Project extends Obj
  ## A project in the DB
  observables: =>
    setup: null
    dependencies: null
    post_dependencies: null
    test: null
    extra: null
    latest_build: null
    hipchat_room: null
    hipchat_api_token: null
    campfire_room: null
    campfire_token: null
    campfire_subdomain: null
    flowdock_api_token: null
    github_user: null
    heroku_deploy_user: null
    ssh_keys: []
    followed: null
    loading_users: false
    users: []
    paying_user: null
    parallel: 1
    loaded_paying_user: false
    trial_parallelism: null
    changed_parallel_settings: null

  constructor: (json) ->

    json.latest_build = (new Build(json.latest_build)) if json.latest_build
    super json

    VcsUrlMixin(@)

    # Make sure @parallel remains an integer
    @editParallel = komp
      read: ->
        @parallel()
      write: (val) ->
        @parallel(parseInt(val))
      owner: @

    @build_url = komp =>
      @vcs_url() + '/build'

    @has_settings = komp =>
      @setup() or @dependencies() or @post_dependencies() or @test() or @extra()

    # TODO: maybe this should return null if there are no plans
    #       should also probably load plans
    @plan = komp =>
      if @paying_user()? and @paying_user().plan
        plans = VM.billing().plans().filter (p) =>
          p.id is @paying_user().plan_id()
        p = plans[0]
        p.max_parallelism = 8
        new Plan plans[0]
      else
        new Plan

    # Allows for user parallelism to trump the plan's max_parallelism
    @plan_max_speed = komp =>
      if @plan().max_parallelism?
        Math.max(@plan().max_parallelism, @max_parallelism())

    @max_parallelism = komp =>
      if @paying_user()? then @paying_user().parallelism() else @trial_parallelism()

    @focused_parallel = ko.observable @parallel()

    @can_select_parallel = komp =>
      if @paying_user()?
        @focused_parallel() <= @max_parallelism()
      else
        false

    @current_user_is_paying_user_p = komp =>
      if @paying_user()?
        @paying_user().login is VM.current_user().login
      else
        false

    @parallel_label_style = (num) =>
      disabled: komp =>
        # weirdly sends num as string when num is same as parallel
        parseInt(num) > @max_parallelism()
      selected: komp =>
        parseInt(num) is @parallel()

    @paying_user_ident = komp =>
      if @paying_user()? then @paying_user().login

    @show_parallel_upgrade_plan_p = komp =>
      @paying_user()? and @plan_max_speed() < @focused_parallel()

    @show_parallel_upgrade_speed_p = komp =>
      @paying_user()? and (@max_parallelism() < @focused_parallel() <= @plan_max_speed())

    @show_retry_latest_build_p = komp =>
      @latest_build() && @latest_build().retry_build && @changed_parallel_settings()

    @focused_parallel_cost_increase = komp =>
      VM.billing().parallelism_cost_difference(@plan(), @max_parallelism(), @focused_parallel())



  @sidebarSort: (l, r) ->
    if l.followed() and r.followed() and l.latest_build()? and r.latest_build()?
      if l.latest_build().build_num > r.latest_build().build_num then -1 else 1
    else if l.followed() and l.latest_build()?
      -1
    else if r.followed() and r.latest_build()?
      1
    else
      if l.vcs_url().toLowerCase() > r.vcs_url().toLowerCase() then 1 else -1

  retry_latest_build: =>
    @latest_build().retry_build()

  speed_description_style: =>
    'selected-label': @focused_parallel() == @parallel()

  disable_parallel_input: (num) =>
    num > @max_parallelism()

  load_paying_user: =>
    $.ajax
      type: "GET"
      url: "/api/v1/project/#{@project_name()}/paying_user"
      success: (result) =>
        if result?
          @paying_user(new User(result))
          @loaded_paying_user(true)

  checkbox_title: =>
    "Add CI to #{@project_name()}"

  unfollow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/unfollow"
      success: (data) =>
        @followed(data.followed)
        _kmq.push(['record', 'Removed A Project']);
        _gaq.push(['_trackEvent', 'Projects', 'Remove']);

  follow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/follow"
      success: (data) =>
        _kmq.push(['record', 'Added A Project']);
        _gaq.push(['_trackEvent', 'Projects', 'Add']);
        if data.first_build
          (new Build(data.first_build)).visit()
        else
          $('html, body').animate({ scrollTop: 0 }, 0);
          @followed(data.followed)
          VM.loadRecentBuilds()

  save_hooks: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/project/#{@project_name()}/settings"
      data: JSON.stringify
        hipchat_room: @hipchat_room()
        hipchat_api_token: @hipchat_api_token()
        campfire_room: @campfire_room()
        campfire_token: @campfire_token()
        campfire_subdomain: @campfire_subdomain()
        flowdock_api_token: @flowdock_api_token()


    false # dont bubble the event up

  save_specs: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/project/#{@project_name()}/settings"
      data: JSON.stringify
        setup: @setup()
        dependencies: @dependencies()
        post_dependencies: @post_dependencies()
        test: @test()
        extra: @extra()
      success: (data) =>
        (new Build(data)).visit()
    false # dont bubble the event up

  set_heroku_deploy_user: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/heroku-deploy-user"
      success: (result) =>
        true
        @refresh()
    false

  clear_heroku_deploy_user: (data, event) =>
    $.ajax
      type: "DELETE"
      event: event
      url: "/api/v1/project/#{@project_name()}/heroku-deploy-user"
      success: (result) =>
        true
        @refresh()
    false

  set_github_user: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/github-user"
      success: (result) =>
        true
        @refresh()
    false

  clear_github_user: (data, event) =>
    $.ajax
      type: "DELETE"
      event: event
      url: "/api/v1/project/#{@project_name()}/github-user"
      success: (result) =>
        true
        @refresh()
    false

  save_ssh_key: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/ssh-key"
      data: JSON.stringify
        hostname: $("#hostname").val()
        public_key: $("#publicKey").val()
        private_key: $("#privateKey").val()
      success: (result) =>
        $("#hostname").val("")
        $("#publicKey").val("")
        $("#privateKey").val("")
        @refresh()
        false
    false

  delete_ssh_key: (data, event) =>
    $.ajax
      type: "DELETE"
      event: event
      url: "/api/v1/project/#{@project_name()}/ssh-key"
      data: JSON.stringify
        fingerprint: data.fingerprint
      success: (result) =>
        @refresh()
        false
    false

  get_users: () =>
    @loading_users(true)
    $.ajax
      type: "GET"
      url: "/api/v1/project/#{@project_name()}/users"
      success: (result) =>
        @users(result)
        @loading_users(false)
        true
    false

  invite_user: (user) =>
    $.ajax
      type: "POST"
      url: "/api/v1/project/#{@project_name()}/invite/#{user.login}"

  refresh: () =>
    $.getJSON "/api/v1/project/#{@project_name()}/settings", (data) =>
      @updateObservables(data)

  set_parallelism: (data, event) =>
    @focused_parallel(@parallel())
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/project/#{@project_name()}/settings"
      data: JSON.stringify
        parallel: @parallel()
      success: =>
        @changed_parallel_settings(true)
      error: (data) =>
        @refresh()
        @load_paying_user()
    true

  parallel_input_id: (num) =>
    "parallel_input_#{num}"

  parallel_focus_in: (place) =>
    if @focusTimeout? then clearTimeout(@focusTimeout)
    @focused_parallel(place)

  parallel_focus_out: (place) =>
    if @focusTimeout? then clearTimeout(@focusTimeout)
    @focusTimeout = window.setTimeout =>
      @focused_parallel(@parallel())
    , 200

class User extends Obj
  observables: =>
    organizations: []
    collaboratorAccounts: []
    loadingOrganizations: false
    loadingRepos: false
    # the org we're currently viewing in add-projects
    activeOrganization: null
    # keyed on org/account name
    repos: []
    tokens: []
    tokenLabel: ""
    herokuApiKeyInput: ""
    heroku_api_key: ""
    user_key_fingerprint: ""
    email_provider: ""
    selected_email: ""
    basic_email_prefs: "smart"
    plan: null
    parallelism: 1

  constructor: (json) ->
    super json,
      admin: false
      login: ""

    @environment = window.renderContext.env

    @showEnvironment = komp =>
      @admin || (@environment is "staging") || (@environment is "development")

    @environmentColor = komp =>
      result = {}
      result["env-" + @environment] = true
      result

    @in_trial = komp =>
      # not @paid and @days_left_in_trial >= 0
      false

    @trial_over = komp =>
      #not @paid and @days_left_in_trial < 0
      # need to figure "paid" out before we really show this
      false

    @showLoading = komp =>
      @loadingRepos() or @loadingOrganizations()

    @plan_id = komp =>
      @plan()

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

  create_user_key: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/user/ssh-key"
      data: JSON.stringify {label: @tokenLabel()}
      success: (result) =>
        @user_key_fingerprint(result.user_key_fingerprint)
        true
    false

  delete_user_key: (data, event) =>
    $.ajax
      type: "DELETE"
      event: event
      url: "/api/v1/user/ssh-key"
      data: JSON.stringify {label: @tokenLabel()}
      success: (result) =>
        @user_key_fingerprint(result.user_key_fingerprint)
        true
    false

  save_heroku_key: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/user/heroku-key"
      data: JSON.stringify {apikey: @herokuApiKeyInput()}
      success: (result) =>
        true
        @heroku_api_key(@herokuApiKeyInput())
        @herokuApiKeyInput("")
    false

  save_preferences: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/user/save-preferences"
      data: JSON.stringify
        basic_email_prefs: @basic_email_prefs()
        selected_email: @selected_email()
      success: (result) =>
        @updateObservables(result)
    false

  save_basic_email_pref: (data, event) =>
    @basic_email_prefs(event.currentTarget.defaultValue)
    @save_preferences(data, event)
    true

  save_email_address: (data, event) =>
    @selected_email(event.currentTarget.defaultValue)
    @save_preferences(data, event)
    true

  loadOrganizations: () =>
    @loadingOrganizations(true)
    $.getJSON '/api/v1/user/organizations', (data) =>
      @organizations(data)
      @setActiveOrganization(data[0])
      @loadingOrganizations(false)


  loadCollaboratorAccounts: () =>
    @loadingOrganizations(true)
    $.getJSON '/api/v1/user/collaborator-accounts', (data) =>
      @collaboratorAccounts(data)
      @loadingOrganizations(false)

  setActiveOrganization: (org, event) =>
    if org
      @activeOrganization(org.login)
      @loadRepos(org)

  loadRepos: (org) =>
    @loadingRepos(true)
    if org.org
      url = "/api/v1/user/org/#{org.login}/repos"
    else
      url = "/api/v1/user/user/#{org.login}/repos"

    $.getJSON url, (data) =>
      @repos((new Repo r for r in data))
      @loadingRepos(false)

  isPaying: () =>
    @plan?

class Plan extends Obj
  constructor: ->
    super

    # Temporary limit so that users don't "block all builds forever"
    @max_purchasable_parallelism  = komp =>
      Math.min(4, @max_parallelism)

    # Change max_purchasable_parallelism to @max_parallelism when we can
    # do unlimited parallelism
    @parallelism_options = ko.observableArray([1..@max_purchasable_parallelism()])

    @concurrency_options = ko.observableArray([1..20])

    @allowsParallelism = komp =>
      @max_parallelism > 1

    @projectsTitle = komp =>
      "#{@projects} project" + (if @projects == 1 then "" else "s")

    @projectsContent = komp =>
      "We'll test up to #{@projects} private repositories."

    @minParallelismContent = komp =>
      "Run your tests at #{@min_parallelism}x the speed."

    @minParallelismDescription = komp =>
      "#{@min_parallelism}x"

    @maxParallelismDescription = komp =>
      "up to #{@max_parallelism}x"

    @pricingDescription = komp =>
      if VM.billing().chosenPlan()? and @.id == VM.billing().chosenPlan().id
        "Your current plan"
      else
        if not @price?
          "Contact us for pricing"
        else
          if VM.billing().chosenPlan()?
            "Switch plan $#{@price}/mo"
          else
            "Sign up now for $#{@price}/mo"


  featureAvailable: (feature) =>
    result =
      tick: not feature.name? or feature.name in @features
    if feature.name?
      result[feature.name] = true
    result

class Billing extends Obj
  observables: =>
    stripeToken: null
    cardInfo: null

    # old data
    oldPlan: null
    oldTotal: 0

    # metadata
    wizardStep: 1
    planFeatures: []
    loadingOrganizations: false

    # new data
    organizations: {}
    chosenPlan: null
    plans: []
    parallelism: 1
    concurrency: 1

  constructor: ->
    super

    @total = komp =>
      @calculateCost(@chosenPlan(), parseInt(@concurrency()), parseInt(@parallelism()))

    @savedCardNumber = komp =>
      return "" unless @cardInfo()
      "************" + @cardInfo().last4

    @wizardCompleted = komp =>
      @wizardStep() > 4

    # Make sure @parallelism remains a number
    @editParallelism = komp
      read: ->
        @parallelism()
      write: (val) ->
        if val? then @parallelism(parseInt(val))
      owner: @

    @editConcurrency = komp
      read: ->
        @concurrency()
      write: (val) ->
        if val? then @concurrency(parseInt(val))
      owner: @

  parallelism_option_text: (plan, p) =>
    "#{p}-way ($#{@parallelism_cost(plan, p)})"

  concurrency_option_text: (plan, c) =>
    "#{c} build#{if c > 1 then 's' else ''} at a time ($#{@concurrency_cost(plan, c)})"

  raw_parallelism_cost: (p) ->
    if p == 1
      0
    else
      Math.round(log2(p) * 99)

  parallelism_cost: (plan, p) =>
    Math.max(0, @calculateCost(plan, null, p) - @calculateCost(plan))
    #Math.max(0, @raw_parallelism_cost(p) - @raw_parallelism_cost(plan.min_parallelism))

  # p2 > p1
  parallelism_cost_difference: (plan, p1, p2) =>
    @parallelism_cost(plan, p2) - @parallelism_cost(plan, p1)

  concurrency_cost: (plan, c) ->
    if plan.concurrency == "Unlimited"
      0
    else
      Math.max(0, @calculateCost(plan, c) - @calculateCost(plan))

  calculateCost: (plan, concurrency, parallelism) ->
    unless plan
      0
    else
      c = concurrency or 0
      extra_c = Math.max(0, c - 1)

      p = parallelism or 1
      p = Math.max(p, 2)
      extra_p = (log2 p) - 1
      extra_p = Math.max(0, extra_p)

      plan.price + (extra_c * 49) + (Math.round(extra_p * 99))

  selectPlan: (plan) =>
    if plan.price?
      @chosenPlan(plan)
      if @wizardCompleted()
        SammyApp.setLocation "/account/plans#card"
      else
        @advanceWizard(2)
    else
      VM.raiseIntercomDialog("I'd like ask about enterprise pricing...\n\n")

  load: (hash="small") =>
    unless @loaded
      @loadPlans()
      @loadPlanFeatures()
      @loadExistingPlans()
      @loadOrganizations()
      @loadStripe()
      @loaded = true

  stripeSubmit: (data, event) ->
    number = $('.card-number').val()
    cvc = $('.card-cvc').val()
    exp_month = $('.card-expiry-month').val()
    exp_year = $('.card-expiry-year').val()

    unless Stripe.validateCardNumber number
      notifyError "Invalid credit card number, please try again."
      event.preventDefault()
      return false

    unless Stripe.validateExpiry exp_month, exp_year
      notifyError "Invalid expiry date, please try again."
      event.preventDefault()
      return false

    unless Stripe.validateCVC cvc
      notifyError "Invalid CVC, please try again."
      event.preventDefault()
      return false

    key = switch renderContext.env
      when "production" then "pk_ZPBtv9wYtkUh6YwhwKRqL0ygAb0Q9"
      else 'pk_Np1Nz5bG0uEp7iYeiDIElOXBBTmtD'
    Stripe.setPublishableKey(key)

    # disable the submit button to prevent repeated clicks
    button = $('.submit-button')
    button.addClass "disabled"

    Stripe.createToken {
      number: number,
      cvc: cvc,
      exp_month: exp_month,
      exp_year: exp_year
    }, (status, response) =>
      if response.error
        button.removeClass "disabled"
        notifyError response.error.message
      else
        @recordStripeTransaction event, response # TODO: add the plan

    # prevent the form from submitting with the default action
    return false;

  stripeUpdate: (data, event) ->
    @recordStripeTransaction event, null

  recordStripeTransaction: (event, stripeInfo) =>
    $.ajax(
      url: "/api/v1/user/pay"
      event: event
      type: if stripeInfo then "POST" else "PUT"
      data: JSON.stringify
        token: stripeInfo
        plan: @chosenPlan().id

      success: () =>
        @cardInfo(stripeInfo.card) if stripeInfo?
        @oldTotal(@total())
        @advanceWizard(3)
    )
    false

  advanceWizard: (new_step) =>
    @wizardStep(Math.max(new_step, @wizardStep() + 1))


  loadStripe: () =>
    $.getScript "https://js.stripe.com/v1/"

  loadExistingPlans: () =>
    $.getJSON '/api/v1/user/existing-plans', (data) =>
      @cardInfo(data.card_info)
      @oldTotal(data.amount / 100)
      @chosenPlan(new Plan(data.plan)) if data.plan
      @concurrency(data.concurrency or 1)
      @parallelism(data.parallelism or 1)
      if @chosenPlan()
        @advanceWizard(5)

  loadOrganizations: () =>
    @loadingOrganizations(true)
    $.getJSON '/api/v1/user/stripe-organizations', (data) =>
      @loadingOrganizations(false)
      @organizations(data)

  saveOrganizations: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/user/organizations"
      data: JSON.stringify
        organizations: @organizations()
      success: =>
        @advanceWizard(4)

  saveParallelism: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/user/parallelism"
      data: JSON.stringify
        parallelism: @parallelism()
        concurrency: @concurrency()
      success: (data) =>
        @oldTotal(@total())
        @advanceWizard(5)


  loadPlans: () =>
    $.getJSON '/api/v1/user/plans', (data) =>
      @plans((new Plan(d) for d in data))

  loadPlanFeatures: () =>
    $.getJSON '/api/v1/user/plan-features', (data) =>
      @planFeatures(data)
      $('.more-info').popover
        html: true
        placement: "bottom"
        trigger: "hover"
        selector: ".more-info"


display = (template, args) ->
  $('#main').html(HAML[template](args))
  ko.applyBindings(VM)


class CircleViewModel extends Base
  constructor: ->
    observableCount = 0
    @ab = (new ABTests(ab_test_definitions)).ab_tests
    @current_user = ko.observable(new User window.renderContext.current_user)
    @build = ko.observable()
    @builds = ko.observableArray()
    @project = ko.observable()
    @projects = ko.observableArray()
    @billing = ko.observable(new Billing)
    @recent_builds = ko.observableArray()
    @build_state = ko.observable()
    @admin = ko.observable()
    @error_message = ko.observable(null)
    @first_login = true;
    @refreshing_projects = ko.observable(false);
    @max_possible_parallelism = ko.observable(24);
    @parallelism_options = ko.observableArray([1..@max_possible_parallelism()])
    observableCount += 8 # are we still doing this?

    @setupPusher()

    @intercomUserLink = komp =>
      @build() and @build() and @projects() # make it update each time the URL changes
      path = window.location.pathname.match("/gh/([^/]+/[^/]+)")
      if path
        "https://www.intercom.io/apps/vnk4oztr/users" +
          "?utf8=%E2%9C%93" +
          "&filters%5B0%5D%5Battr%5D=custom_data.pr-followed" +
          "&filters%5B0%5D%5Bcomparison%5D=contains&filters%5B0%5D%5Bvalue%5D=" +
          path[1]


  setupPusher: () =>
    key = switch renderContext.env
      when "production" then "6465e45f8c4a30a2a653"
      else "3f8cb51e8a23a178f974"

    @pusher = new Pusher(key, { encrypted: true})

    Pusher.channel_auth_endpoint = "/auth/pusher"

    @userSubscribePrivateChannel()
    @pusherSetupBindings()

  userSubscribePrivateChannel: () =>
    channel_name = "private-" + @current_user().login
    @user_channel = @pusher.subscribe(channel_name)
    @user_channel.bind('pusher:subscription_error', (status) -> notifyError status)

  pusherSetupBindings: () =>
    @user_channel.bind "call", (data) =>
      this[data.fn].apply(this, data.args)

  testCall: (arg) =>
    alert(arg)

  clearErrorMessage: () =>
    @error_message null

  setErrorMessage: (message) =>
    if message == "" or not message?
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

      @projects(projects)
      window.time_taken_projects = Date.now() - start_time
      if @first_login
        @first_login = false

  available_projects: () => komp =>
    (p for p in @projects() when not p.followed())

  followed_projects: () => komp =>
    (p for p in @projects() when p.followed())

  has_followed_projects: () => komp =>
    @followed_projects()().length > 0

  refresh_project_src: () => komp =>
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
    if window._gaq? # we dont use ga in test mode
      _gaq.push(['_trackPageview', '/dashboard'])
    display "dashboard", {}

  loadAddProjects: (cx) =>
    @current_user().loadOrganizations()
    @current_user().loadCollaboratorAccounts()
    display "add_projects", {}

  loadProject: (cx, username, project) =>
    project_name = "#{username}/#{project}"
    @builds.removeAll()
    $.getJSON "/api/v1/project/#{project_name}", (data) =>
      start_time = Date.now()
      @builds((new Build d for d in data))
      window.time_taken_project = Date.now() - start_time
    display "project", {project: project_name}


  loadBuild: (cx, username, project, build_num) =>
    project_name = "#{username}/#{project}"
    @build(null)
    $.getJSON "/api/v1/project/#{project_name}/#{build_num}", (data) =>
      start_time = Date.now()
      @build(new Build data)
      @build().maybeSubscribe()

      window.time_taken_build = Date.now() - start_time


    display "build", {project: project_name, build_num: build_num}


  loadEditPage: (cx, username, project, subpage) =>
    project_name = "#{username}/#{project}"

    subpage = subpage[0].replace('#', '')
    subpage = subpage || "settings"

    # if we're already on this page, dont reload
    if (not @project() or
    (@project().vcs_url() isnt "https://github.com/#{project_name}"))
      $.getJSON "/api/v1/project/#{project_name}/settings", (data) =>
        @project(new Project data)
        @project().get_users()
        if subpage is "parallel_builds"
          @project().load_paying_user()
          @billing().load()

    else if subpage is "parallel_builds"
      @project().load_paying_user()
      @billing().load()

    $('#main').html(HAML['edit']({project: project_name}))
    $('#subpage').html(HAML['edit_' + subpage]({}))
    ko.applyBindings(VM)


  loadAccountPage: (cx, subpage) =>
    subpage = subpage[0].replace(/\//, '') # first one
    subpage = subpage.replace(/\//g, '_')
    subpage = subpage.replace(/-/g, '_')
    [subpage, hash] = subpage.split('#')
    subpage or= "notifications"
    hash or= "meta"

    if subpage.indexOf("plans") == 0
      @billing().load()
    $('#main').html(HAML['account']({}))
    $('#subpage').html(HAML['account_' + subpage]({}))
    $("##{subpage}").addClass('active')
    if $('#hash').length
      $("##{hash}").addClass('active')
      $('#hash').html(HAML['account_' + subpage + "_" + hash]({}))
    ko.applyBindings(VM)


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
    window.TestTargets =
      log2: log2
      Billing: Billing
      ansiToHtml: ansiToHtml
    $.getScript "/assets/js/tests/inner-tests.js.dieter"

  raiseIntercomDialog: (message) =>
    unless intercomJQuery?
      notifyError "Uh-oh, our Help system isn't available. Please email us instead, at <a href='mailto:sayhi@circleci.com'>sayhi@circleci.com</a>!"
      return

    jq = intercomJQuery
    jq("#IntercomTab").click()
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

  goDashboard: (data, event) =>
    # signature so this can be used as knockout click handler
    window.SammyApp.setLocation("/")

  # use in ko submit binding, expects button to submit form
  mockFormSubmit: (cb) =>
    (formEl) =>
      $formEl = $(formEl)
      $formEl.find('button').addClass 'disabled'
      if cb? then cb.call()
      false

window.VM = new CircleViewModel()
window.SammyApp = Sammy '#app', () ->
    @get('^/tests/inner', (cx) -> VM.loadJasmineTests(cx))

    @get('^/', (cx) => VM.loadDashboard(cx))
    @get('^/add-projects', (cx) => VM.loadAddProjects(cx))
    @get('^/gh/:username/:project/edit(.*)',
      (cx) -> VM.loadEditPage cx, cx.params.username, cx.params.project, cx.params.splat)
    @get('^/account(.*)',
      (cx) -> VM.loadAccountPage(cx, cx.params.splat))
    @get('^/gh/:username/:project/:build_num',
      (cx) -> VM.loadBuild cx, cx.params.username, cx.params.project, cx.params.build_num)
    @get('^/gh/:username/:project',
      (cx) -> VM.loadProject cx, cx.params.username, cx.params.project)

    @get('^/logout', (cx) -> VM.logout(cx))

    @get('^/admin', (cx) -> VM.loadAdminPage cx)
    @get('^/admin/users', (cx) -> VM.loadAdminPage cx, "users")
    @get('^/admin/projects', (cx) -> VM.loadAdminProjects cx)
    @get('^/admin/recent-builds', (cx) -> VM.loadAdminRecentBuilds cx)
    @get('^/admin/build-state', (cx) -> VM.loadAdminBuildState cx)
    @get('^/docs(.*)', (cx) -> # go to the outer app
      SammyApp.unload()
      window.location = cx.path)

    @get('^(.*)', (cx) -> VM.unsupportedRoute(cx))

    # dont show an error when posting
    @post '^/circumvent-sammy', (cx) -> true
    @post '^/logout', -> true
    @post '^/admin/switch-user', -> true

    # Google analytics
    @bind 'event-context-after', ->
      if window._gaq? # we dont use ga in test mode
        window._gaq.push @path





$(document).ready () ->
  SammyApp.run window.location.pathname.replace(/(.+)\/$/, "$1")
  _kmq.push(['identify', VM.current_user().login])




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
