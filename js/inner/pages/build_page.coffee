CI.inner.BuildPage = class BuildPage extends CI.inner.Page
  observables: =>
    _.extend super,
      name: "build"
      username: null
      project: null
      project_name: null
      build_num: null
      mention_branch: true

  constructor: (properties) ->
    super(properties)
    title = @komp =>
      "##{@build_num} - #{@username}/#{@project}"
