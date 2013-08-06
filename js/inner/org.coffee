CI.inner.Org = class Org extends CI.inner.Obj
  observables: =>
    name: null
    projects: []
    users: []
    paid: false
    plan: null

  clean: () ->
    super
    VM.cleanObjs(@project_objs())

  constructor: (json) ->

    super json

    @projects(new CI.inner.Project(project) for project in @projects())

    # projects that have been turned into Project objects
    @project_objs = @komp =>
      for project in @projects()
        project.followers = (new CI.inner.User(u) for u in project.followers)
        project

    @projects_with_followers = @komp =>
      _.filter @project_objs(), ((p) -> p.followers.length)

    @projects_without_followers = @komp =>
      _.reject @project_objs(), ((p) -> p.followers.length)