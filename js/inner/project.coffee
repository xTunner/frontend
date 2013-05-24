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
    github_user: null
    heroku_deploy_user: null
    ssh_keys: []
    followed: null
    loading_users: false
    users: []
    parallel: 1
    paying_user: null
    loaded_paying_user: null
    trial_parallelism: null
    retried_build: null
    branches: null
    default_branch: null
    show_all_branches: false

  constructor: (json) ->

    super json

    @latest_build(@compute_latest_build())

    CI.inner.VcsUrlMixin(@)

    @parallelism_options = [1..24]
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

    ## Parallelism
    @billing = new CI.inner.Billing

    @containers_p = @komp =>
      @billing.chosen_plan_containers_p()

    # Allows for user parallelism to trump the plan's max_parallelism
    @plan_max_speed = @komp =>
      if @billing.chosenPlan() && @billing.chosenPlan().max_parallelism?
        @billing.chosenPlan().max_parallelism

    @paid_speed = @komp =>
      if @containers_p()
        Math.min @plan_max_speed(), @billing.containers()
      else
        @billing.parallelism()

    @focused_parallel = ko.observable @parallel()

    @payor_login = @komp =>
      @billing.payor() && @billing.payor().login

    @current_user_is_payor_p = @komp =>
      @payor_login() is VM.current_user().login

    @parallel_label_style = (num) =>
      disabled: @komp =>
        # weirdly sends num as string when num is same as parallel
        parseInt(num) > @paid_speed()
      selected: @komp =>
        parseInt(num) is @parallel()

    @show_parallel_upgrade_plan_p = @komp =>
      @plan_max_speed() && @plan_max_speed() < @focused_parallel()

    @show_parallel_upgrade_speed_p = @komp =>
      @paid_speed() && @plan_max_speed && (@paid_speed() < @focused_parallel() <= @plan_max_speed())

    ## Sidebar
    @branch_names = @komp =>
      names = (k for own k, v of @branches())
      names.sort()

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

  speed_description_style: =>
    'selected-label': @focused_parallel() == @parallel()

  disable_parallel_input: (num) =>
    num > @paid_speed()

  load_billing: =>
    $.ajax
      type: "GET"
      url: "/api/v1/project/#{@project_name()}/plan"
      success: (result) =>
        @billing.loadPlanData(result)

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
        hipchat_notify: @hipchat_notify()
        hipchat_notify_prefs: @hipchat_notify_prefs()
        campfire_room: @campfire_room()
        campfire_token: @campfire_token()
        campfire_subdomain: @campfire_subdomain()
        campfire_notify_prefs: @campfire_notify_prefs()
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
