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
    messages: []
    final_out: []
    trailing_out: ""
    step: null
    index: null
    has_output: null
    retrieved_output: false
    retrieving_output: false
    user_minimized: null # tracks whether the user explicitly minimized. nil means they haven't touched it


  constructor: (json, build) ->
    super json

    @build = build

    # these should be the only options
    @success = @komp => @status() == "success"
    @running = @komp => @status() == "running"
    @failed = @komp => @status() == "failed" or @status() == "timedout" or @status() == "cancelled" || @status() == "infrastructure_fail"
    @infrastructure_fail = @komp => @status() == "infrastructure_fail"


    # Expand failing actions
    @minimize = @komp =>
      if @user_minimized()?
        @user_minimized()
      else
        @success() and not @messages().length > 0

    if !@minimize()
      @maybe_retrieve_output()

    @visible = @komp =>
      not @minimize()

    @has_content = @komp =>
      @has_output() or ( @final_out()? and @final_out().length > 0) or @bash_command()

    @action_header_style =
      # knockout CSS requires a boolean observable for each of these
      minimize: @minimize
      contents: @has_content

      # see circle.model.action/compute-status for the list
      failed: @komp => @failed() or @infrastructure_fail()
      running: @komp => @running()
      success: @komp => @success()

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
      if @run_time_millis()
        CI.time.as_duration(@run_time_millis())
      else if @start_time()
        CI.time.as_duration(@updatingDuration(@start_time()))

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

    @stdoutConverter = CI.terminal.ansiToHtmlConverter("brblue")
    @stderrConverter = CI.terminal.ansiToHtmlConverter("red")

  toggle_minimize: =>
    if not @user_minimized?
      @user_minimized(!@user_minimized())
    else
      @user_minimized(!@minimize())
    if not @user_minimized()
      @maybe_retrieve_output()
    @user_minimized()

  htmlEscape: (str) =>
    str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  append_output: (new_out) =>
    i = 0
    while i < new_out.length
      type = new_out[i].type
      converter = if type == 'err'
                    @stderrConverter
                  else
                    @stdoutConverter
      offset = 0
      sequence = (while (i + offset < new_out.length) and (new_out[i + offset].type is type)
                    new_out[i + offset++])
      @final_out.push(converter.append(@htmlEscape((o.message for o in sequence).join "")))
      i += sequence.length
    @trailing_out(@stdoutConverter.get_trailing() + @stderrConverter.get_trailing())

  report_build: () =>
    VM.raiseIntercomDialog('I think I found a bug in Circle at ' + window.location + '\n\n')

  maybe_retrieve_output: () =>
    if @has_output() and !@retrieved_output() and !@retrieving_output()
      @retrieving_output(true)
      url = if @output_url
              @output_url
            else
              "/api/v1/project/#{@build.project_name()}/#{@build.build_num}/output/#{@step()}/#{@index()}"
      $.ajax
        url: url
        type: "GET"
        success: (data) =>
          @retrieved_output(true)
          ## reset the converters
          @stdoutConverter = CI.terminal.ansiToHtmlConverter("brblue")
          @stderrConverter = CI.terminal.ansiToHtmlConverter("red")
          @append_output(data)
        complete: (data, status) =>
          @retrieving_output(false)

class Step extends CI.inner.Obj

  observables: =>
    actions: []

  clean: () =>
    super
    VM.cleanObjs(@actions())

  constructor: (json) ->
    json.actions = if json.actions? then (new CI.inner.ActionLog(j) for j in json.actions) else []
    super json
