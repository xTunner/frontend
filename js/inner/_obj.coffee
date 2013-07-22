# Update this observable every second so that we can get updating durations
# and intervals
window.updator = ko.observable(0)

setInterval () ->
  window.updator(window.updator() + 1)
, 1000

CI.inner.Obj = class Obj
  constructor: (json={}, defaults={}) ->
    @updator = window.updator

    @komps = []

    for k,v of @observables()
      @[k] = @observable(v)

    for k,v of $.extend {}, defaults, json
      if @observables().hasOwnProperty(k) then @[k](v) else @[k] = v

  observables: () => {}

  komps: []

  komp: (args...) =>
    comp = ko.computed args...
    @komps.push(comp)
    comp

  observable: (obj) ->
    if $.isArray obj
      ko.observableArray obj
    else
      ko.observable obj

  updateObservables: (obj) =>
    for k,v of obj
      if @observables().hasOwnProperty(k)
        @[k](v)

  # Meant to be used in a computed observable. Updates every second and returns
  # the number of millis between now and start.
  # It's best to use this in an else branch, so that it's not evaluated after the
  # duration no longer needs to update.
  updatingDuration: (start, f, o) =>
    @updator()
    console.log('updating', f)
    moment().diff(start)

  clean: () =>
    for k in @komps
      k.dispose()

CI.inner.VcsUrlMixin = (obj) ->
  obj.vcs_url = ko.observable(if obj.vcs_url then obj.vcs_url else "")

  obj.observables.vcs_url = obj.vcs_url

  obj.project_name = obj.komp ->
    obj.vcs_url().substring(19)

  obj.project_display_name = obj.komp ->
    obj.project_name().replace("/", '/\u200b')

  obj.project_path = obj.komp ->
    "/gh/#{obj.project_name()}"

  obj.edit_link = obj.komp () =>
    "#{obj.project_path()}/edit"
