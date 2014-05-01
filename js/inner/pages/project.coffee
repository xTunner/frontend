CI.inner.ProjectPage = class ProjectPage extends CI.inner.DashboardPage
  constructor: (properties) ->
    @username = null
    @project = null

    super(properties)

    @crumbs = [new CI.inner.OrgCrumb(@username),
               new CI.inner.ProjectCrumb(@username, @project, {active: true}),
               new CI.inner.ProjectSettingsCrumb(@username, @project)]

    @project_name = "#{@username}/#{@project}"
    @title = "#{@username}/#{@project}"

    @show_branch = true

  refresh: () ->
    page = @current_page()
    if page is 0
      VM.loadProject(@username, @project, null, page, true)
