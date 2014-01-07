CI.inner.Build = class Build extends CI.inner.Obj
  observables: =>
    messages: []
    build_time_millis: null
    all_commit_details: []
    committer_name: null
    committer_email: null
    committer_date: null
    author_name: null
    author_email: null
    author_date: null
    body: null
    start_time: null
    stop_time: null
    queued_at: null
    steps: []
    containers: []  # actions belong to containers
    current_container: null
    status: null
    lifecycle: null
    outcome: null
    failed: null
    infrastructure_fail: null
    dont_build: null
    name: null
    branch: "unknown"
    previous: null
    previous_successful_build: null
    retry_of: null
    subject: null
    parallel: null
    usage_queued_at: null
    usage_queue_why: null
    usage_queue_visible: false
    has_artifacts: false
    artifacts: null
    artifacts_visible: false
    pusher_subscribed: false
    ssh_enabled: false
    rest_commits_visible: false
    node: []

  clean: () =>
    # pusher fills the console with errors if you unsubscribe
    # from a channel you weren't subscribed to
    if @pusher_subscribed() then VM.pusher.unsubscribe(@pusherChannel())

    super
    VM.cleanObjs(@steps())
    @clean_usage_queue_why()

  constructor: (json) ->
    steps = json.steps or []

    super(json)

    CI.inner.VcsUrlMixin(@)

    @steps(new CI.inner.Step(s, @) for s in steps)
    # Find out what the JSON really looks like so containers can be pre-populated
    #
    #      "steps" : [ {
    #          "name" : "configure the build",
    #          "actions" : [ {
    #            "bash_command" : null,
    #            "run_time_millis" : 1646,
    #            "start_time" : "2013-02-12T21:33:38Z",
    #            "end_time" : "2013-02-12T21:33:39Z",
    #            "name" : "configure the build",
    #            "command" : "configure the build",
    #            "exit_code" : null,
    #            "type" : "infrastructure",
    #            "index" : 0,
    #            "status" : "success",
    #          } ] },
    #          "name" : "lein2 deps",
    #          "actions" : [ {
    #            "bash_command" : "lein2 deps",
    #            "run_time_millis" : 7555,
    #            "start_time" : "2013-02-12T21:33:47Z",
    #            "command" : "((lein2 :deps))",
    #            "messages" : [ ],
    #            "step" : 1,
    #            "exit_code" : 0,
    #            "end_time" : "2013-02-12T21:33:54Z",
    #            "index" : 0,
    #            "status" : "success",
    #            "type" : "dependencies",
    #            "source" : "inference",
    #            "failed" : null
    #          } ] }
    #
    # So... iterate over steps, actions. Push the actions into new lists for containers
    #
    # Basically just a 2D list of actions per step. Transpose to form actions
    # per container

    # This is truly horrible...
    containers = []
    for step in @steps()
      console.log("Step")
      for action in step.actions()
        if not containers[action.index()]?
          console.log("create new container list")
          containers[action.index()] = []
        console.log("append to container list")
        containers[action.index()].push(action)
        console.log("container list size:" + containers[action.index()].length)

    @containers(new CI.inner.Container("C" + index, index, action_list, @) for action_list, index in containers)
    console.log("Built containers - " + containers.length)
    console.log("Number of steps - " + steps.length)

    @current_container(@containers()[0])

    # FIXME This more than likely won't dynamically update :(
    @foo_height_bar = @komp =>
      $current_container = $("#" + @current_container.container_id)
      console.log("foo_height_bar")
      console.log($current_container)
      return { height: $current_container.css("height") }

    @url = @komp =>
      @urlForBuildNum @build_num

    @important_style = @komp =>
      switch @status()
        when "failed"
          true
        when "timedout"
          true
        when "no_tests"
          true
        else
          false

    @warning_style = @komp =>
      switch @status()
        when "infrastructure_fail"
          true
        when "killed"
          true
        when "not_run"
          true
        else
          false

    @success_style = @komp =>
      switch @outcome()
        when "success"
          true
        else
          false

    @info_style = @komp =>
      switch @status()
        when "running"
          true
        else
          false

    @no_style = @komp =>
      switch @status()
        when "queued"
          true
        when "not_running"
          true
        when "scheduled"
          true
        when "retried"
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

    @favicon_color = @komp =>
      if @important_style()
        'red'
      else if @warning_style()
        'orange'
      else if @success_style()
        'green'
      else if @info_style()
        'blue'
      else if @no_style()
        'grey'

    @canceled = @komp =>
      @status() == 'canceled'

    @queued = @komp =>
      @status() == 'queued'

    @scheduled = @komp =>
      @status() == 'scheduled'

    @finished = @komp =>
      @stop_time()? or @canceled()

    @status_icon_class =
      "fa-check": @success_style
      "fa-times": @komp => @important_style() || @warning_style() || @canceled()
      "fa-clock-o": @komp => @queued()
      "fa-refresh": @komp => @info_style()
      "fa-calendar-o": @komp => @scheduled()

    @status_words = @komp => switch @status()
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

    @why_in_words = @komp =>
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

    @ssh_enabled_now = @komp =>
      # ssh_enabled is undefined before Enabled SSH is completed
      @ssh_enabled() and @node() and _.every(@node(), (n) -> n.ssh_enabled != false)

    @can_cancel = @komp =>
      if @status() == "canceled"
        false
      else
        switch @lifecycle()
          when "not_running"
            true
          when "running"
            true
          when "queued"
            true
          when "scheduled"
            true
          else
            false

    @pretty_start_time = @komp =>
      if @start_time()
        window.updator() # update every second
        CI.time.as_time_since(@start_time())

    @previous_build = @komp =>
      @previous()? and @previous().build_num

    @duration = @komp () =>
      if @start_time() and @stop_time()
        CI.time.as_duration(moment(@stop_time()).diff(moment(@start_time())))
      else
        if @status() == "canceled"
          # build was canceled from the queue
          "canceled"
        else if @start_time()
          duration_millis = @updatingDuration(@start_time())
          CI.time.as_duration(duration_millis) + @estimated_time(duration_millis)

    # don't try to show queue information if the build is pre-usage_queue
    @show_queued_p = @komp =>
      @usage_queued_at()?

    @usage_queued = @komp =>
      not @finished() and not @queued_at()?

    @run_queued = @komp =>
      not @finished() and @queued_at()? and not @start_time()?

    @run_queued_time = @komp =>
      if @start_time() and @queued_at()
        moment(@start_time()).diff(@queued_at())
      else if @queued_at() and @stop_time() # canceled before left queue
        moment(@stop_time()).diff(@queued_at())
      else if @queued_at()
        @updatingDuration(@queued_at())

    @usage_queued_time = @komp =>
      if @usage_queued_at() and @queued_at()
        moment(@queued_at()).diff(@usage_queued_at())
      else if @usage_queued_at() and @stop_time() # canceled before left queue
        moment(@stop_time()).diff(@usage_queued_at())
      else if @usage_queued_at()
        @updatingDuration(@usage_queued_at())

    @queued_time = @komp =>
      (@run_queued_time() || 0) + (@usage_queued_time() || 0)

    @queued_time_summary = @komp =>
      if @run_queued_time()
        "#{CI.time.as_duration(@usage_queued_time())} waiting + #{CI.time.as_duration(@run_queued_time())} in queue"
      else
        "#{CI.time.as_duration(@usage_queued_time())} waiting for builds to finish"

    @branch_in_words = @komp =>
      return "unknown" unless @branch()
      @branch().replace(/^remotes\/origin\//, "")

    @trimmed_branch_in_words = @komp =>
      CI.stringHelpers.trimMiddle(@branch_in_words(), 23)

    @github_url = @komp =>
      return unless @vcs_revision
      "#{@vcs_url()}/commit/#{@vcs_revision}"

    @branch_url = @komp =>
      return unless @branch
      "#{@project_path()}/tree/#{@branch()}"

    @github_revision = @komp =>
      return unless @vcs_revision
      @vcs_revision.substring 0, 7

    @author = @komp =>
      @author_name() or @author_email()

    @committer = @komp =>
      @committer_name() or @committer_email()

    @committer_mailto = @komp =>
      if @committer_email()
        "mailto:#{@committer_email}"

    @author_mailto = @komp =>
      if @committer_email()
        "mailto:#{@committer_email()}"

    @author_isnt_committer = @komp =>
      (@committer_email() isnt @author_email()) or (@committer_name() isnt @author_name())

    @head_commits = @komp =>
      # careful not to modify the all_commit_details array here
      @all_commit_details().slice(-3).reverse()

    @rest_commits = @komp =>
      # careful not to modify the all_commit_details array here
      @all_commit_details().slice(0,-3).reverse()

    @tooltip_title = @komp =>
      @status_words() + ": " + @build_num

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

  estimated_time: (current_build_millis) =>
    valid = (estimated_millis) ->
      estimate_is_not_too_low = current_build_millis < estimated_millis * 1.5
      estimate_is_positive = estimated_millis > 0

      return estimate_is_positive and estimate_is_not_too_low

    if @previous_successful_build()?
      estimated_millis = @previous_successful_build().build_time_millis

      if valid estimated_millis
        return " / ~" + CI.time.as_estimated_duration(estimated_millis)
    ""

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
      @pusher_subscribed(true)
      @build_channel = VM.pusher.subscribe(@pusherChannel())
      @build_channel.bind 'pusher:subscription_error', (status) ->
        _rollbar.push status

      @build_channel.bind('newAction', @newAction)
      @build_channel.bind('updateAction', @updateAction)
      @build_channel.bind('appendAction', @appendAction)
      @build_channel.bind('updateObservables', @updateObservables)
      @build_channel.bind('maybeAddMessages', @maybeAddMessages)

  maybeSubscribeObservables: () =>
    if @shouldSubscribe()
      @pusher_subscribed(true)
      @build_channel = VM.pusher.subscribe(@pusherChannel())
      @build_channel.bind 'pusher:subscription_error', (status) ->
        _rollbar.push status
      @build_channel.bind('updateObservables', @updateObservables)

  fillActions: (step, index) =>
    # fills up steps and actions such that step and index are valid
    for i in [0..step]
      if not @steps()[i]?
        @steps.setIndex(i, new CI.inner.Step({}))

    # actions can arrive out of order when doing parallel. Fill up the other indices so knockout doesn't bitch
    for i in [0..index]
      if not @steps()[step].actions()[i]?
        @steps()[step].actions.setIndex(i, new CI.inner.ActionLog({}, @))

    # fill up containers
    for i in [0..index]
      if not @containers()[i]?
        @containers.setIndex(i, new CI.inner.Container("C" + i, i, [], @))

    # fill up actions in containers
    for i in [0..step]
      if not @containers()[index].actions()[i]?
        @containers.setIndex(i, new CI.inner.ActionLog({}, @))

  newAction: (json) =>
    @fillActions(json.step, json.index)
    if old = @steps()[json.step].actions()[json.index]
      old.clean()
    action_log = new CI.inner.ActionLog(json.log, @)
    @steps()[json.step].actions.setIndex(json.index, action_log)
    @containers()[json.index].actions.setIndex(json.step, action_log)
    console.log("newAction step:" + json.step + ", index:" + json.index)

  updateAction: (json) =>
    # updates the observables on the action, such as end time and status.
    @fillActions(json.step, json.index)
    @steps()[json.step].actions()[json.index].updateObservables(json.log)
    console.log("updateAction step:" + json.step + ", index:" + json.index)

  appendAction: (json) =>
    # adds output to the action
    @fillActions(json.step, json.index)

    @steps()[json.step].actions()[json.index].append_output([json.out])
    console.log("appendAction step:" + json.step + ", index:" + json.index)

  maybeAddMessages: (json) =>
    existing = (message.message for message in @messages())
    (@messages.push(msg) if msg.message not in existing) for msg in json

  trackRetryBuild: (data, clearCache, SSH) =>
    mixpanel.track("Trigger Build",
      "vcs-url": data.vcs_url.substring(19)
      "build-num": data.build_num
      "retry?": true
      "clear-cache?": clearCache
      "ssh?": SSH)

  retry_build: (data, event, clearCache) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/retry"
      type: "POST"
      event: event
      success: (data) =>
        console.log("retry build data", data)
        console.log("retry event", event)
        VM.visit_local_url data.build_url
        @trackRetryBuild data, clearCache, false
    false

  clear_cache_and_retry_build: (data, event) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/build-cache"
      type: "DELETE"
      event: event
      success: (data) =>
        @retry_build data, event, true
    false

  ssh_build: (data, event) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/ssh"
      type: "POST"
      event: event
      success: (data) =>
        VM.visit_local_url data.build_url
        @trackRetryBuild data, false, true
    false

  cancel_build: (data, event) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/cancel"
      type: "POST"
      event: event
    false

  toggle_usage_queue_why: () =>
    if @usage_queue_visible()
      @usage_queue_visible(!@usage_queue_visible())
      @clean_usage_queue_why()
      @usage_queue_why(null)
    else
      @load_usage_queue_why()
      @usage_queue_visible(true)

  toggle_rest_commits: () =>
    @rest_commits_visible(!@rest_commits_visible())

  clean_usage_queue_why: () =>
    if @usage_queue_why()
      VM.cleanObjs(@usage_queue_why())

  load_usage_queue_why: () =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/usage-queue"
      type: "GET"
      success: (data) =>
        @clean_usage_queue_why()
        @usage_queue_why(new CI.inner.Build(build_data) for build_data in data.reverse())
      complete: () =>
        # stop the spinner if there was an error
        @usage_queue_why([]) if not @usage_queue_why()
        _.each(@usage_queue_why(), ((b) -> b.maybeSubscribeObservables()))

  toggle_artifacts: () =>
    if @artifacts_visible()
      @artifacts_visible(!@artifacts_visible())
      @artifacts(null)
    else
      @load_artifacts()
      @artifacts_visible(true)

  clean_artifacts: () =>
    @artifacts(null)

  load_artifacts: () =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/artifacts"
      type: "GET"
      success: (data) =>
        @clean_artifacts()
        data = for artifact in data
                 artifact.pretty_path = artifact.pretty_path.replace "$CIRCLE_ARTIFACTS/", ""
                 artifact.pretty_path = CI.stringHelpers.trimMiddle artifact.pretty_path, 80
                 artifact
        @artifacts(data)
      complete: () =>
        # stop the spinner if there was an error
        @artifacts([]) if not @artifacts()

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

  ssh_connection_string: (node) =>
    str = "ssh "
    [port, username, ip] = [node.port, node.username, node.public_ip_addr || node.ip_addr]
    if port
      str += "-p #{port} "
    if username
      str += "#{username}@#{ip}"
    str

  # TODO Needs to be split into:
  #   1) set the currently active container
  #   2) switch the viewport to that container
  # Then we can drop output from other containers to save some memory
  # We can also have "<<" and ">>" buttons
  select_container: (container) =>
    console.log("selected container " + container.name)
    @current_container(container)

    @switch_viewport(@current_container())

  switch_viewport: (container) =>
    $container_parent = $("#container_parent")
    console.log($container_parent)

    $element = $("#" + container.container_id)
    console.log($element)
    console.log($element.offset())

    crazy_parent_offset = $container_parent.offset().left

    delta = $element.offset().left - crazy_parent_offset

    offset = $container_parent.scrollLeft() + delta

    $container_parent.stop().animate({scrollLeft: offset}, 250)

# TODO the next challenge is to set the height of $("#container_parent) to be
# whatever the currently selected container height is, and update dynamically
