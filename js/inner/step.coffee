CI.inner.Step = class Step extends CI.inner.Obj
  observables: =>
    actions: []

  constructor: (json) ->
    json.actions = if json.actions? then (new CI.inner.ActionLog(j) for j in json.actions) else []
    super json
