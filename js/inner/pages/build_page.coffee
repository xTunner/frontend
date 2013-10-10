CI.inner.BuildPage = class BuildPage extends CI.inner.Page
  constructor: (properties) ->
    @username = null
    @project = null
    @project_name = null
    @build_num = null
    @mention_branch = true

    super(properties)
    @name = "build"
    @project_name = "#{@username}/#{@project}"
    @title = "##{@build_num} - #{@project_name}"

    @crumbs = ['project', 'branch', 'build', 'project_settings']

  favicon_color: =>
    if VM.build()?
      VM.build().favicon_color()
