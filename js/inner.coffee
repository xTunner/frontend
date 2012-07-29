window.observableCount = 0

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

$(document).ajaxError (ev, xhr, status, errorThrown) ->
  finishAjax(xhr.event, "data-failed-text", "Failed")
  if xhr.responseText.indexOf("<!DOCTYPE") is 0
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

  obj.project_path = komp ->
    "/gh/#{obj.project_name()}"

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
    status: null
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

    @action_header_button_style =
      @action_header_style

    @action_log_style =
      minimize: @minimize

    @start_to_end_string = komp =>
      "#{@start_time()} to #{@end_time()}"

    @duration = Circle.time.as_duration(@run_time_millis)

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
      "<span class='#{o.type}'>#{@htmlEscape(o.message)}</span>"
    x.join ""

  appendLog: (json) =>
    @out.push(json.out)


class Step extends Obj

  observables: =>
    actions: []

  constructor: (json) ->
    json.actions = if json.actions? then (new ActionLog(j) for j in json.actions) else []
    super json

class Build extends Obj
  observables: =>
    messages: []
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
    failed: null
    infrastructure_fail: null
    dont_build: null
    name: null
    branch: "unknown"
    previous: null
    retry_of: null
    subject: null,

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
      switch @status()
        when "success"
          true
        when "fixed"
          true
        when "deploy"
          true
        else
          false

    @notice_style = komp =>
      switch @status()
        when "running"
          true
        else
          false

    @style =
      important: @important_style
      warning: @warning_style

      success: @success_style
      notice: @notice_style
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

    @pretty_start_time = komp =>
      if @start_time()
        Circle.time.as_time_since(@start_time())

    @previous_build = komp =>
      @previous()? and @previous().build_num

    @duration = komp () =>
      if @build_time_millis
        Circle.time.as_duration(@build_time_millis)
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
    @isRunning() or @status() == "queued"

  maybeSubscribe: () =>
    if @shouldSubscribe()
      @build_channel = VM.pusher.subscribe(@pusherChannel())
      @build_channel.bind('pusher:subscription_error', (status) -> notifyError status)

      @build_channel.bind('newAction', (json) => @newAction json)
      @build_channel.bind('updateAction', (json) => @updateAction json)
      @build_channel.bind('appendAction', (json) => @appendAction json)
      @build_channel.bind('updateObservables', (json) =>
        @updateObservables(json))

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
    @steps()[json.step].actions()[json.index].out.push(json.out)

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

class Project extends Obj

  observables: =>
    setup: null
    dependencies: null
    test: null
    extra: null
    latest_build: null
    hipchat_room: null
    hipchat_api_token: null
    campfire_room: null
    campfire_token: null
    campfire_subdomain: null
    heroku_deploy_user: null
    followed: null

  constructor: (json) ->

    json.latest_build = (new Build(json.latest_build)) if json.latest_build
    super json

    VcsUrlMixin(@)

    @edit_link = komp () =>
      "#{@project_path()}/edit"

    @build_url = komp =>
      @vcs_url() + '/build'

    @has_settings = komp =>
      @setup() or @dependencies() or @test() or @extra()



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

  unfollow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/unfollow"
      success: (data) =>
        @followed(data.followed)

  follow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/follow"
      success: (data) =>
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


    false # dont bubble the event up

  save_specs: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/project/#{@project_name()}/settings"
      data: JSON.stringify
        setup: @setup()
        dependencies: @dependencies()
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


  refresh: () =>
    $.getJSON "/api/v1/project/#{@project_name()}/settings", (data) =>
      @updateObservables(data)

class User extends Obj
  observables: =>
    tokens: []
    tokenLabel: ""
    herokuApiKeyInput: ""
    heroku_api_key: ""
    user_key_fingerprint: ""

  constructor: (json) ->
    super json,
      admin: false
      login: ""
      basic_email_prefs: "all"

    @environment = window.renderContext.env

    @showEnvironment = komp =>
      @admin || (@environment is "staging") || (@environment is "development")

    @environmentColor = komp =>
      result = {}
      result["env-" + @environment] = true
      result

    @in_trial = komp =>
      not @paid and @days_left_in_trial >= 0

    @trial_over = komp =>
      not @paid and @days_left_in_trial < 0


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
      data: JSON.stringify {basic_email_prefs: @basic_email_prefs}
    false # dont bubble the event up



class Plan extends Obj
  constructor: ->
    super

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
      if @price?
        "Sign up now for $#{@price}/mo"
      else
        "Contact us for pricing"

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
    planFeatures: []

    # new data
    availableOrganizations: null
    existingOrganizations: null
    chosenPlan: null
    plans: []
    selectedParallelism: 1
    selectedConcurrency: 1


  constructor: ->
    super

    @defaultPlan = komp =>
      for p in @plans()
        if p.default
          return p

    @selectedPlan = komp =>
      if @chosenPlan()?
        @chosenPlan()
      else if @oldPlan()?
        @oldPlan()
      else
        @defaultPlan()

    @total = komp =>
      if @chosenPlan()?
        @chosenPlan().price
      else
        0

    @notPaid = komp =>
      false

    @savedCardNumber = komp =>
      return "" unless @cardInfo()
      "************" + @cardInfo().last4

  selectPlan: (plan) =>
    @chosenPlan(plan)
    SammyApp.setLocation("/account/plans/card")


  load: (hash="small") =>
    unless @loaded
      $('.more-info').popover({html: true, placement: "below", live: true})
      @loadPlans()
      @loadPlanFeatures()
      @loadExistingPlans()
      @loadStripe()
      #      @loaded = true

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
        SammyApp.setLocation "/account/plans"
    )
    false


  loadStripe: () =>
    $.getScript "https://js.stripe.com/v1/"

  loadExistingPlans: () =>
    $.getJSON '/api/v1/user/existing-plans', (data) =>
      @cardInfo(data.card_info)
      @oldTotal(data.amount / 100)
      @chosenPlan(data.plan)

  loadTeamMembers: () =>
    $.getJSON '/api/v1/user/team-members', (data) =>
      @teamMembers(data)

  loadPlans: () =>
    $.getJSON '/api/v1/user/plans', (data) =>
      @plans((new Plan(d) for d in data))

  loadPlanFeatures: () =>
    $.getJSON '/api/v1/user/plan-features', (data) =>
      @planFeatures(data)




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
    @billing = ko.observable(new Billing)
    @recent_builds = ko.observableArray()
    @build_state = ko.observable()
    @admin = ko.observable()
    @error_message = ko.observable(null)
    @first_login = true;
    @refreshing_projects = ko.observable(false);
    observableCount += 8

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
        # make sure users aren't seeing the blank page
        setTimeout(@loadProjects, 1000)
        setTimeout(@loadProjects, 4000)
        setTimeout(@loadProjects, 7000)
        setTimeout(@loadProjects, 15000)
        setTimeout(@loadProjects, 30000)


  available_projects: () => komp =>
    (p for p in @projects() when not p.followed())

  followed_projects: () => komp =>
    (p for p in @projects() when p.followed())

  has_followed_projects: () => komp =>
    @followed_projects()().length > 0

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
      @build().maybeSubscribe()

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


  loadAccountPage: (cx, subpage) =>
    subpage = subpage[0].replace(/\//, '') # first one
    subpage = subpage.replace(/\//g, '_')
    subpage or= "notifications"

    if subpage.indexOf("plans") == 0
      @billing().load()
    $('#main').html(HAML['account']({}))
    $('#subpage').html(HAML['account_' + subpage.replace(/-/g, '_')]({}))
    $("##{subpage}").addClass('active')
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
    $.get "/assets/js/tests/inner-tests.dieter", (code) =>
      eval code

  raiseIntercomDialog: (message) =>
    unless intercomJQuery?
      notifyError "Uh-oh, our Help system isn't available. Please email us instead, at <a href='mailto:sayhi@circleci.com'>sayhi@circleci.com</a>!"
      return

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
window.SammyApp = Sammy '#app', () ->
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
    @get('/docs(.*)', (cx) -> # go to the outer app
      SammyApp.unload()
      window.location = cx.path)

    @get('(.*)', (cx) -> VM.unsupportedRoute(cx))

    # dont show an error when posting
    @post '/logout', -> true
    @post '/admin/switch-user', -> true

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
