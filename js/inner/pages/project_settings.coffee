CI.inner.ProjectSettingsPage = class ProjectSettingsPage extends CI.inner.Page
  constructor: (properties) ->
    @crumbs = ['project', 'project_settings']
    @username = null
    @project = null

    super(properties)
    @project_name = "#{@username}/#{@project}"
    @title = "Edit settings - #{@username}/#{@project}"
