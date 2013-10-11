CI.inner.ProjectPage = class ProjectPage extends CI.inner.Page
  constructor: (properties) ->
    @crumbs = [] # ['project', 'project_settings']
    @username = null
    @project = null

    super(properties)
    @project_name = "#{@username}/#{@project}"
    @title = "Edit settings - #{@username}/#{@project}"
