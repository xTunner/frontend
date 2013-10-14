CI.inner.ProjectPage = class ProjectPage extends CI.inner.Page
  constructor: (properties) ->
    @username = null
    @project = null

    super(properties)

    @crumbs = [new CI.inner.ProjectCrumb(@username, @project, {active: true}),
               new CI.inner.ProjectSettingsCrumb(@username, @project)]

    @project_name = "#{@username}/#{@project}"
    @title = "#{@username}/#{@project}"

  refresh: () ->
    VM.loadProject(@username, @project, null, true)
