CI.inner.Container = class Container extends CI.inner.Obj
  observables: =>
    actions: []

  constructor: (name, index, actions, build) ->
    if not name?
      name = "None"
    super { name: name, has_multiple_actions: 1 }
    @build = build
    @actions(actions)
    @container_index = index
    @container_id = "container_" + @container_index
    console.log("Created container: " + JSON.stringify(@, null, 2))

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
      child_styles.reduce(reducer, { success: true, failed: false, running: false })

    @position_style = 
      left: (@container_index * 100) + "%"

    @container_class = @komp =>
      # Everything about this function is icky
      #   - It shouldn't need to be a ko.computed, the value is static
      #   - The dynamic property syntax is ugly, coffeescript supports interpolation but not "foo#{name}": value
      o = {}
      o["container_" + @container_id] = true
      return o

  success: () ->
    for action in @actions()
      if not action.success()
        return false
    return true

  running: () =>
    for action in @actions()
      if action.running()
        return true
    return false

  failed: () =>
    for action in @actions()
      if action.failed()
        return true
    return false

  jquery_element: () =>
    $("#" + @container_id)
