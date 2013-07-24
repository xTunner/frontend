CI.inner.Navbar = class Navbar extends CI.inner.Obj
  constructor: (selected, vm) ->
    @vm = vm # local pointer so we can get the build

    # we're sharing these observables with the VM
    @selected = selected
    @current_build = @vm.build

    @current_build_branch = @komp =>
      if @current_build() then @current_build().branch()

    @page = @komp => @selected().page
    @crumbs = @komp => @selected().crumbs
    @username = @komp => @selected().username
    @project = @komp => @selected().project
    @project_name = @komp => @selected().project_name
    @branch = @komp => @selected().branch || @current_build_branch()
    @build_num = @komp => @selected().build_num

    @project_path = @komp =>
      if @username() and @project()
        @projectPath(@username(), @project())

    @project_settings_path = @komp => @project_path() + '/edit'

    @project_branch_path = @komp =>
      if @username() and @project()
        @projectPath(@username(), @project(), @branch())

    @build_path = @komp =>
      if @username() and @project() and @build_num()
        "#{@project_path()}/#{@build_num()}"

    @label_class = (page) =>
      @komp =>
        'label-active': @page() is page

  projectPath: (username, project, branch) ->
    project_name = "#{username}/#{project}"
    path = "/gh/#{project_name}"
    path += "/tree/#{encodeURIComponent(branch)}" if branch?
    path
