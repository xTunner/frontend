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
    hipchat_notify: false
    hipchat_notify_prefs: null
    campfire_room: null
    campfire_token: null
    campfire_subdomain: null
    campfire_notify_prefs: null
    flowdock_api_token: null
    irc_server: null
    irc_channel: null
    irc_keyword: null
    irc_username: null
    irc_password: null
    github_user: null
    heroku_deploy_user: null
    ssh_keys: []
    followed: null
    loading_users: false
    users: []
    parallel: 1
    retried_build: null
    branches: null
    default_branch: null
    show_all_branches: false
    tokens: []
    tokenLabel: ""
    tokenScope: "status"
    env_vars: []
    env_varName: ""
    env_varValue: ""
    show_branch_input: false
    settings_branch: null
    show_test_new_settings: false

  constructor: (json) ->

    super json

    @latest_build(@compute_latest_build())

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

    @settings_branch(@default_branch())

    @has_settings = @komp =>
      @setup() or @dependencies() or @post_dependencies() or @test() or @extra()

    ## Parallelism
    @billing = new CI.inner.Billing
      org_name: @org_name()

    # use safe defaults in case chosenPlan is null
    @plan = @komp =>
      @billing.chosenPlan() || new CI.inner.Plan

    @parallelism_options = @komp =>
      [1..Math.max(@plan().max_parallelism(), 24)]

    # Trial parallelism is counted as paid here
    @paid_parallelism = @komp =>
      Math.min @plan().max_parallelism(), @billing.containers()

    @focused_parallel = ko.observable @parallel()

    @parallel_label_style = (num) =>
      disabled: @komp =>
        # weirdly sends num as string when num is same as parallel
        parseInt(num) > @paid_parallelism()
      selected: @komp =>
        parseInt(num) is @parallel()
      bad_choice: @komp =>
        parseInt(num) <= @paid_parallelism() && @billing.containers() % parseInt(num) isnt 0

    @show_upgrade_plan = @komp =>
      @plan().max_parallelism() < @focused_parallel()

    @show_add_containers = @komp =>
      @paid_parallelism() < @focused_parallel() <= @plan().max_parallelism()

    @show_uneven_divisor_warning_p = @komp =>
      @focused_parallel() <= @paid_parallelism() && @billing.containers() % @focused_parallel() isnt 0

    @simultaneous_builds = @komp =>
      Math.floor(@billing.containers() / @focused_parallel())

    @show_number_of_simultaneous_builds_p = @komp =>
      @focused_parallel() <= @paid_parallelism()

    ## Sidebar
    @branch_names = @komp =>
      names = (k for own k, v of @branches())
      names.sort()

    @pretty_branch_names = @komp =>
      decodeURIComponent(name) for name in @branch_names()

    @personal_branch_p = (branch_name) =>
      if branch_name is @default_branch()
        true
      else if @branches()[branch_name] and @branches()[branch_name].pusher_logins
        VM.current_user().login in @branches()[branch_name].pusher_logins
      else
        false

    @personal_branches = () =>
      @branch_names().filter (name) =>
        @personal_branch_p(name)

    @branch_names_to_show = @komp =>
      if @show_all_branches()
        @branch_names()
      else
        @personal_branches()

    @show_toggle_branches = @komp =>
      not (@branch_names().toString() is @personal_branches().toString())

    @toggle_show_all_branches = () =>
      @show_all_branches(!@show_all_branches())

    @sorted_builds = (branch_name) =>
      if @branches()[branch_name]
        recent = @branches()[branch_name].recent_builds or []
        running = @branches()[branch_name].running_builds or []
        recent.concat(running).sort(Project.buildSort)
      else
        []

    @latest_branch_build = (branch_name) =>
      build = @sorted_builds(branch_name)[0]
      if build
        new CI.inner.Build(build)

    @recent_branch_builds = (branch_name) =>
      builds = @sorted_builds(branch_name)[0..4].reverse()
      new CI.inner.Build(b) for b in builds

    @build_path = (build_num) =>
      @project_path() + "/" + build_num

    @branch_path = (branch_name) =>
      "#{@project_path()}/tree/#{branch_name}"

    @active_style = (project_name, branch) =>
      if VM.selected().project_name is project_name
        if VM.selected().branch
          if decodeURIComponent(branch) is VM.selected().branch
            {selected: true}
        else if not branch
          {selected: true}

    # Forces the notification preference true/false checkbox value to
    # convert to either smart or null
    @translate_checked = (pref_observable) =>
      ko.computed
        read: () ->
          pref_observable()
        write: (newVal) ->
          if newVal then pref_observable("smart") else pref_observable(null)

  @sidebarSort: (l, r) ->
    if l.followed() and r.followed() and l.latest_build()? and r.latest_build()?
      if l.latest_build().build_num > r.latest_build().build_num then -1 else 1
    else if l.followed() and l.latest_build()?
      -1
    else if r.followed() and r.latest_build()?
      1
    else
      if l.vcs_url().toLowerCase() > r.vcs_url().toLowerCase() then 1 else -1

  @buildTimeSort: (l, r) ->
    if !l.pushed_at and !r.pushed_at
      0
    else if !l.pushed_at
      1
    else if !r.pushed_at
      -1
    else if new Date(l.pushed_at) > new Date(r.pushed_at)
      -1
    else if new Date(l.pushed_at) < new Date(r.pushed_at)
      1
    else
      0

  @buildNumSort: (l, r) ->
    if l.build_num > r.build_num
      -1
    else if l.build_num < r.build_num
      1
    else
      0

  @buildSort: (l, r) ->
    time_sort = Project.buildTimeSort(l, r)
    if time_sort is 0
      Project.buildNumSort(l, r)
    else
      time_sort

  compute_latest_build: () =>
    if @branches()? and @branches()[@default_branch()] and @branches()[@default_branch()].recent_builds?
      new CI.inner.Build @branches()[@default_branch()].recent_builds[0]

  format_branch_name: (name, len) =>
    decoded_name = decodeURIComponent(name)
    if len?
      CI.stringHelpers.trimMiddle(decoded_name, len)
    else
      decoded_name

  retry_latest_build: =>
    @latest_build().retry_build()

  parallelism_description_style: =>
    'selected-label': @focused_parallel() == @parallel()

  disable_parallel_input: (num) =>
    num > @paid_parallelism()

  load_billing: =>
    $.ajax
      type: "GET"
      url: "/api/v1/project/#{@project_name()}/plan"
      success: (result) =>
        @billing.loadPlanData(result)

  checkbox_title: =>
    "Add CI to #{@project_name()}"

  unfollow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/unfollow"
      success: (data) =>
        @followed(data.followed)
        _gaq.push(['_trackEvent', 'Projects', 'Remove']);
        VM.loadProjects() # refresh sidebar

  follow: (data, event, callback) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/follow"
      success: (data) =>
        @followed(data.followed)
        _gaq.push(['_trackEvent', 'Projects', 'Add'])
        if callback? then callback()

  follow_and_maybe_visit: (data, event) =>
    callback = (data) =>
      if data.first_build
        VM.visit_local_url data.build_url
      else
        $('html, body').animate({ scrollTop: 0 }, 0);
        @followed(data.followed)
        VM.loadRecentBuilds()
      VM.loadProjects() # refresh sidebar

    @follow(data, event, callback)

  save_hooks: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/project/#{@project_name()}/settings"
      data: JSON.stringify
        hipchat_room: @hipchat_room()
        hipchat_api_token: @hipchat_api_token()
        hipchat_notify: @hipchat_notify()
        hipchat_notify_prefs: @hipchat_notify_prefs()
        campfire_room: @campfire_room()
        campfire_token: @campfire_token()
        campfire_subdomain: @campfire_subdomain()
        campfire_notify_prefs: @campfire_notify_prefs()
        flowdock_api_token: @flowdock_api_token()
        irc_server: @irc_server()
        irc_channel: @irc_channel()
        irc_keyword: @irc_keyword()
        irc_username: @irc_username()
        irc_password: @irc_password()


    false # dont bubble the event up

  toggle_show_branch_input: (data, event) =>
    @show_branch_input(!@show_branch_input())
    $(event.target).tooltip('hide')
    # hasfocus binding is bad here: closes the form when you click the button
    if @show_branch_input()
      $(event.target).siblings("input").focus()

  save_dependencies: (data, event) =>
    @save_specs data, event, =>
      window.location.hash = "#tests"

  save_specs: (data, event, callback) =>
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
      success: () =>
        if callback
          callback.call(data, event)
    false # dont bubble the event up

  create_settings_build: (data, event) =>
    url = "/api/v1/project/#{@project_name()}"
    if not _.isEmpty(@settings_branch())
      url += "/tree/#{encodeURIComponent(@settings_branch())}"
    $.ajax
      type: "POST"
      event: event
      url: url
      success: (data) =>
        VM.visit_local_url data.build_url
    false # dont bubble the event up

  save_and_create_settings_build: (data, event) =>
    @save_specs data, event, @create_settings_build

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
        @show_test_new_settings(true)
      error: (data) =>
        @refresh()
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

  load_tokens: () =>
    $.getJSON "/api/v1/project/#{@project_name()}/token", (data) =>
      @tokens(data)

  create_token: (data, event) =>
    $.ajax
      event: event
      type: "POST"
      url: "/api/v1/project/#{@project_name()}/token",
      data: JSON.stringify
        label: @tokenLabel()
        scope: @tokenScope()
      success: (result) =>
        @tokenLabel("")
        @load_tokens()
    false

  delete_token: (data, event) =>
    $.ajax
      type: "DELETE"
      url: "/api/v1/project/#{@project_name()}/token/#{data.token}",
      success: (result) =>
        @load_tokens()
    false

  load_env_vars: () =>
    $.getJSON "/api/v1/project/#{@project_name()}/envvar", (data) =>
      @env_vars(data)

  create_env_var: (data, event) =>
    $.ajax
      event: event
      type: "POST"
      url: "/api/v1/project/#{@project_name()}/envvar",
      data: JSON.stringify
        name: @env_varName()
        value: @env_varValue()
      success: (result) =>
        @env_varName("")
        @env_varValue("")
        @load_env_vars()
      false

  delete_env_var: (data, event) =>
    $.ajax
      type: "DELETE"
      url: "/api/v1/project/#{@project_name()}/envvar/#{data.name}",
      success: (result) =>
        @load_env_vars()
    false
