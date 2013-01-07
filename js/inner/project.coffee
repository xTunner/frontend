CI.inner.Project = class Project extends CI.inner.Obj
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
    retried_build: null

  constructor: (json) ->

    json.latest_build = (new CI.inner.Build(json.latest_build)) if json.latest_build
    super json

    CI.inner.VcsUrlMixin(@)

    # Make sure @parallel remains an integer
    @editParallel = @komp
      read: ->
        @parallel()
      write: (val) ->
        @parallel(parseInt(val))
      owner: @

    @build_url = @komp =>
      @vcs_url() + '/build'

    @has_settings = @komp =>
      @setup() or @dependencies() or @post_dependencies() or @test() or @extra()

    # TODO: maybe this should return null if there are no plans
    #       should also probably load plans
    @plan = @komp =>
      if @paying_user()? and @paying_user().plan
        plans = VM.billing().plans().filter (p) =>
          p.id is @paying_user().plan_id()
        p = plans[0]
        new CI.inner.Plan plans[0]
      else
        new CI.inner.Plan

    # Allows for user parallelism to trump the plan's max_parallelism
    @plan_max_speed = @komp =>
      if @plan().max_parallelism?
        Math.max(@plan().max_parallelism, @max_parallelism())

    @max_parallelism = @komp =>
      if @paying_user()? then @paying_user().parallelism() else @trial_parallelism()

    @focused_parallel = ko.observable @parallel()

    @can_select_parallel = @komp =>
      if @paying_user()?
        @focused_parallel() <= @max_parallelism()
      else
        false

    @current_user_is_paying_user_p = @komp =>
      if @paying_user()?
        @paying_user().login is VM.current_user().login
      else
        false

    @parallel_label_style = (num) =>
      disabled: @komp =>
        # weirdly sends num as string when num is same as parallel
        parseInt(num) > @max_parallelism()
      selected: @komp =>
        parseInt(num) is @parallel()

    @paying_user_ident = @komp =>
      if @paying_user()? then @paying_user().login

    @show_parallel_upgrade_plan_p = @komp =>
      @paying_user()? and @plan_max_speed() < @focused_parallel()

    @show_parallel_upgrade_speed_p = @komp =>
      @paying_user()? and (@max_parallelism() < @focused_parallel() <= @plan_max_speed())

    @focused_parallel_cost_increase = @komp =>
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
          @paying_user(new CI.inner.User(result))
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
          (new CI.inner.Build(data.first_build)).visit()
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
        (new CI.inner.Build(data)).visit()
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
      success: (data) =>
        @retried_build(new CI.inner.Build(data))
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
