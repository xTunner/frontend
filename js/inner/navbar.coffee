CI.inner.Navbar = class Navbar extends CI.inner.Obj
  constructor: (@selected, @build) -> # we're sharing these observables with the VM
    @build_branch = @komp =>
      if @build() then @build().branch()

    # unpack selected
    @crumbs = @komp => @selected().crumbs
    @page = @komp => @selected().page
    @username = @komp => @selected().username
    @project = @komp => @selected().project
    @project_name = @komp => @selected().project_name
    @sel_branch = @komp => @selected().branch
    @build_num = @komp => @selected().build_num

    @branch = @komp => @sel_branch() or "..."

    @build_branch.subscribe (new_val) =>
      sel = @selected()
      sel.branch = new_val
      @selected(sel)

    @project_path = @komp =>
      if @username() and @project()
        @projectPath(@username(), @project())

    @branch_name = @komp =>
      if @branch()?
        CI.stringHelpers.trimMiddle(@branch(), 45)

    @branch_path = @komp =>
      @projectPath(@username(), @project(), @branch())

    @project_settings_name = @komp => "Edit settings"

    @project_settings_path = @komp => @project_path() + '/edit'

    @build_path = @komp =>
      "#{@project_path()}/#{@build_num()}"

    @build_name = @komp =>
      "build: #{@build_num()}"

    @org_name = @komp => @username()
    @org_path = @komp => "/gh/#{@username()}"

    @org_settings_name = @komp => "Organization settings"
    @org_settings_path = @komp => "/gh/organizations/#{@username()}/settings"

    @label_class = (page) =>
      @komp =>
        'label-active': @page() is page

  crumb_path: (crumb) =>
    @["#{crumb}_path"]

  crumb_name: (crumb) =>
    @["#{crumb}_name"]

  projectPath: (username, project, branch) ->
    project_name = "#{username}/#{project}"
    path = "/gh/#{project_name}"
    path += "/tree/#{encodeURIComponent(branch)}" if branch?
    path
