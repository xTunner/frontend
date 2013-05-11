CI.inner.ActionLog = class ActionLog extends CI.inner.Obj
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
    step: null
    index: null
    has_output: null
    retrieved_output: false
    retrieving_output: false
    user_minimized: null # tracks whether the user explicitly minimized. nil means they haven't touched it


  constructor: (json, build) ->
    super json

    @build = build
    @success = @komp =>
      @status() == "success"

    @failed = @komp => @status() == "failed" or @status() == "timedout" or @status() == "cancelled"
    @infrastructure_fail = @komp => @status() == "infrastructure_fail"

    # Expand failing actions
    @minimize = @komp =>
      if @user_minimized()?
        @user_minimized()
      else
        @success()

    if !@minimize()
      @retrieve_output()

    @visible = @komp =>
      not @minimize()

    @has_content = @komp =>
      @has_output() or ( @out()? and @out().length > 0) or @bash_command()

    @action_header_style =
      # knockout CSS requires a boolean observable for each of these
      minimize: @minimize
      contents: @has_content
      running: @komp => @status() == "running"
      timedout: @komp => @status() == "timedout"
      success: @komp => @status() == "success"
      failed: @komp => @status() == "failed"
      cancelled: @komp => @status() == "cancelled"

    @collapse_icon =
      "icon-chevron-up": @komp => !@minimize()
      "icon-chevron-down": @minimize

    @action_header_button_style = @komp =>
      if @has_content()
        @action_header_style
      else
        {}

    @action_log_style =
      minimize: @minimize

    @start_to_end_string = @komp =>
      "#{@start_time()} to #{@end_time()}"

    @duration = @komp =>
      CI.time.as_duration(@run_time_millis())

    @sourceText = @komp =>
      switch @source()
        when "db"
          "UI"
        when "template"
          "standard"
        else
          @source()

    @sourceTitle = @komp =>
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
    if not @user_minimized()
      @retrieve_output()
    @user_minimized()

  htmlEscape: (str) =>
    str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  log_output: =>
    return "" unless @out()
    x = for o in @out()
      "<p class='#{o.type}'><span class='bubble'></span>#{CI.terminal.ansiToHtml(@htmlEscape(o.message))}</p>"
    x.join ""

  report_build: () =>
    VM.raiseIntercomDialog('I think I found a bug in Circle at ' + window.location + '\n\n')

  retrieve_output: () =>
    if @has_output() and !@retrieved_output() and !@retrieving_output()
      @retrieving_output(true)
      $.ajax
        url: "/api/v1/project/#{@build.project_name()}/#{@build.build_num}/output/#{@step()}/#{@index()}"
        type: "GET"
        success: (data) =>
          @retrieved_output(true)
          @out(data)
        complete: (data, status) =>
          @retrieving_output(false)

class Step extends CI.inner.Obj

  observables: =>
    actions: []

  constructor: (json) ->
    json.actions = if json.actions? then (new CI.inner.ActionLog(j) for j in json.actions) else []
    super json
