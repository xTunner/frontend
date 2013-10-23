CI.inner.ProjectSettingsPage = class ProjectSettingsPage extends CI.inner.ProjectPage
  constructor: (properties) ->
    super(properties)
    @crumbs = [new CI.inner.ProjectCrumb(@username, @project),
               new CI.inner.ProjectSettingsCrumb(@username, @project, {active: true})]

    @title = "Edit settings - #{@username}/#{@project}"
