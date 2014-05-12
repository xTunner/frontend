CI.inner.ProjectBranchPage = class ProjectBranchPage extends CI.inner.DashboardPage
  constructor: (properties) ->
    @username = null
    @project = null

    super(properties)

    @crumbs = [new CI.inner.OrgCrumb(@username),
               new CI.inner.ProjectCrumb(@username, @project),
               new CI.inner.ProjectBranchCrumb(@username, @project, ko.computed =>
                 @branch
               {active: true})
               new CI.inner.ProjectSettingsCrumb(@username, @project)]

    @title = "#{@branch} - #{@username}/#{@project}"

  refresh: () ->
    page = @page_number()
    if page is 0
      VM.loadProject(@username, @project, @branch, page, true)
