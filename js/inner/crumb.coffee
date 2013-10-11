CI.inner.Crumb = class Crumb extends CI.inner.Obj
  constructor: () ->
    @page = null # a CI.inner.Page, used for setting css style

  styles: () =>
    @komp =>
      'label-active': @page? and VM.current_page() instanceof @page

  name: () =>
    # user-visible name of this crumb. must override
    null

  path: () =>
    # url-path to where this crumb goes when you click it
    null


CI.inner.ProjectCrumb = class ProjectCrumb extends Crumb

  constructor: (@username, @project) ->
    super()
    @page = CI.inner.ProjectPage

  name: () =>
    "#{@username}/#{@project}"

  path: () =>
    CI.paths.project_path(@username, @project)


CI.inner.BuildCrumb = class BuildCrumb extends Crumb

  constructor: (@username, @project, @build_num) ->
    super()
    @page = CI.inner.BuildPage

  name: () =>
    "build #{@build_num}"

  path: () =>
    CI.paths.build_path(@username, @project, @build_num)
