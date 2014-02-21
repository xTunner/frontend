CI.inner.Container = class Container extends CI.inner.Obj
  observables: =>
    actions: []

  constructor: (name, index, actions, build) ->
    if not name?
      name = "None"
    super { name: name }

    @build = build
    @actions(actions)
    @container_index = index
    @container_id = _.uniqueId("container_")

    @status_style = @komp =>
      # Result from calling action_log.action_header_style is a map
      # { failed: <val>
      #   running: <val>
      #   success: <val> }
      #
      # combine with these rules:
      # all children == success { success: true } -> success reduces with 'and'
      # any child == failure { failure: true } -> failure reduces with 'or'
      # any child == running { running: true } -> running reduces with 'or'
      reducer = (accum, e) ->
        success: accum.success and e.success()
        failed: accum.failed or e.failed()
        running: accum.running or e.running()

      child_styles = (action.action_header_style for action in @actions())

      # This is horrible, but works. Failed is reported immediately, success
      # only happens when the build is finished
      if child_styles.length > 0
        style = child_styles.reduce(reducer, { success: true, failed: false, running: false })
        if not style.failed and not @build.finished()
          {success: false, failed: false, running: true}
        else
          style
      else
        # assume running if there are no child actions
        {success: false, failed: false, running: true}

    @position_style = 
      left: (@container_index * 100) + "%"

  jquery_element: () =>
    $("#" + @container_id)

  deselect: () =>
    for action in @actions()
      action.maybe_drop_output()

  select: () =>
    for action in @actions()
      action.maybe_retrieve_output()

  clean: () =>
    super
    VM.cleanObjs(@actions())
