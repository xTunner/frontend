CI.inner.ProjectBranchPage = class ProjectBranchPage extends CI.inner.ProjectPage
  constructor: (properties) ->
    super(properties)

    @crumbs = [new CI.inner.ProjectCrumb(@username, @project),
               new CI.inner.ProjectBranchCrumb(@username, @project, ko.computed =>
                 @branch
               {active: true})
               new CI.inner.ProjectSettingsCrumb(@username, @project)]

    @title = "#{@branch} - #{@username}/#{@project}"

  refresh: () ->
    VM.loadProject(@username, @project, null, true)
