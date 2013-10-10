CI.inner.BuildPage = class BuildPage extends CI.inner.Page
  observables: =>
      username: null
      project: null
      project_name: null
      build_num: null
      mention_branch: true

  constructor: (properties) ->
    super(properties)
    @name = "build"
    @title = "##{@build_num()} - #{@username()}/#{@project()}"

    @crumbs = ['project', 'branch', 'build', 'project_settings']
