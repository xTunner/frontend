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

    # projects that have been turned into Project objects
    @project_objs = @komp =>
      for project in @projects()
        project.follower_logins = (u.login for u in project.followers)
        project.followers = (new CI.inner.User(u) for u in project.followers)
        new CI.inner.Project(project)

    # users that have been turned into User objects
    @user_objs = @komp =>
      for user in @users()
        user.projects = _.filter @project_objs(), (p) -> user.login in p.follower_logins
        new CI.inner.User(user)

    @projects_with_followers = @komp =>
      _.filter @project_objs(), ((p) -> p.followers.length)

    @projects_without_followers = @komp =>
      _.reject @project_objs(), ((p) -> p.followers.length)
