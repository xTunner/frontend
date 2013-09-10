CI.inner.Org = class Org extends CI.inner.Obj
  observables: =>
    name: null
    projects: []
    users: []
    paid: false
    plan: null
    subpage: "projects"
    billing: null

  clean: () ->
    super
    VM.cleanObjs(@project_objs())

  constructor: (json) ->

    super json

    @billing new CI.inner.Billing
      organization: @name()

    # Note: we don't create the org until we have the user/projects data
    @loaded = @komp =>
      @billing().loaded()

    # projects that have been turned into Project objects
    @project_objs = @komp =>
      for project in @projects()
        project.follower_logins = (u.login for u in project.followers)
        project.followers = _(new CI.inner.User(u) for u in project.followers)
          .sortBy "login"
        new CI.inner.Project(project)

    # users that have been turned into User objects
    @user_objs = @komp =>
      users = for user in @users()
        user.projects = _.chain(@project_objs())
          .filter((p) -> user.login in p.follower_logins)
          .sortBy((p) -> p.repo_name())
          .value()

        new CI.inner.User(user)

      _.sortBy users, (u) -> -1 * u.projects.length

    @projects_with_followers = @komp =>
      _.chain(@project_objs())
        .filter((p) -> p.followers.length)
        .sortBy((p) -> -1 * p.followers.length)
        .value()

    @projects_without_followers = @komp =>
      _.chain(@project_objs())
        .reject((p) -> p.followers.length)
        .sortBy((p) -> p.repo_name())
        .value()

  followProjectHandler: (project) =>
    callback = (data) =>
      VM.loadOrgSettings(@name())
    (data, event) => project.follow(data, event, callback)