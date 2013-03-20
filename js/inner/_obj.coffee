CI.inner.Obj = class Obj
  constructor: (json={}, defaults={}) ->
    for k,v of @observables()
      @[k] = @observable(v)

    for k,v of $.extend {}, defaults, json
      if @observables().hasOwnProperty(k) then @[k](v) else @[k] = v

  observables: () => {}


  komp: (args...) =>
    ko.computed args...

  observable: (obj) ->
    if $.isArray obj
      ko.observableArray obj
    else
      ko.observable obj

  updateObservables: (obj) =>
    for k,v of obj
      if @observables().hasOwnProperty(k)
        @[k](v)


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
