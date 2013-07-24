CI.inner.Navbar = class Navbar extends CI.inner.Obj
  constructor: (selected, vm) ->
    @vm = vm # local pointer so we can get the build

    # we're sharing these observables with the VM
    @selected = selected
    @current_build = @vm.build

    @current_build_branch = @komp =>
      if @current_build() then @current_build().branch()

    @project_active = @komp => @selected().page is 'project'
    @project_branch_active = @komp => @selected().page is 'project_branch'
    @project_settings_active = @komp => @selected().page is 'project_settings'
    @build_active = @komp => @selected().page is 'build'

    @username = @komp => @selected().username
    @project = @komp => @selected().project
    @project_name = @komp => @selected().project_name
    @branch = @komp => @selected().branch || @current_build_branch()
    @build_num = @komp => @selected().build_num

    @show_crumbs = @komp =>
      @project_active() || @project_branch_active() || @project_settings_active() || @build_active()

    @show_project_branch = @komp =>
      @project_branch_active() || (@build_active() && @branch())

    @show_project_settings = @komp =>
      not @build_active()


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

    @active_class = (page) =>
      @komp =>
        label_active = switch page
          when 'project' then @project_active()
          when 'project_branch' then @project_branch_active()
          when 'project_settings' then @project_settings_active()
          when 'build' then @build_active()
          else false

        {'label-active': label_active}

  projectPath: (username, project, branch) ->
    project_name = "#{username}/#{project}"
    path = "/gh/#{project_name}"
    path += "/tree/#{encodeURIComponent(branch)}" if branch?
    path
