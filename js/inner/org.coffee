CI.inner.Org = class Org extends CI.inner.Obj
  observables: =>
    projects: []
    users: []
    paid: false
    plan: null

  constructor: (json) ->

    super json
