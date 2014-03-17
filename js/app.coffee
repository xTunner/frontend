CI.ajax.init()

display = (template, args, subpage, hash) ->
  klass = 'inner'

  header =
    $("<header></header>")
      .append(HAML.header(args))

  content =
    $("<main></main>")
      .append(HAML[template](args))

  footer =
    $("<footer></footer>")
      .append(HAML["footer"](args))

  $('#app')
    .html("")
    .removeClass('outer')
    .removeClass('inner')
    .addClass(klass)
    .append(header)
    .append(content)
    .append(footer)


  if subpage
    $('#subpage').html(HAML["#{template}_#{subpage}"](args))
    $("##{subpage}").addClass('active')
  if $('#hash').length
    $('#hash').html(HAML["#{template}_#{subpage}_#{hash}"](args))
    $("##{hash}").addClass('active')

  if VM.current_page().title
    document.title = "#{VM.current_page().title} - CircleCI"
  else
    document.title = "Continuous Integration and Deployment - CircleCI"

  ko.applyBindings(VM)

splitSplat = (cx) ->
  p = cx.params.splat[0]
  p = p.replace(/-/g, '_').replace(/\//g, '')
  p.split('#')


class CI.inner.CircleViewModel extends CI.inner.Foundation

  constructor: ->
    super()

    # outer
    @home = new CI.outer.Home("home", "Continuous Integration and Deployment")
    @about = new CI.outer.About("about", "About Us", "View About")
    @pricing = new CI.outer.Page("pricing", "Plans and Pricing", "View Pricing Outer")
    @docs = new CI.outer.Docs("docs", "Documentation", "View Docs")
    @error = new CI.outer.Error("error", "Error")

    @jobs = new CI.outer.Page("jobs", "Work at CircleCI")
    @enterprise = new CI.outer.Page("enterprise", "CircleCI for the enterprise")
    @privacy = new CI.outer.Page("privacy", "Privacy", "View Privacy")
    # @contact = new CI.outer.Page("contact", "Contact us", "View Contact")
    @security = new CI.outer.Page("security", "Security", "View Security", {addLinkTargets: true})

    @sticky_help_is_open = ko.observable(false)

    # inner
    @build = ko.observable()
    @builds = ko.observableArray()
    @project = ko.observable()
    @projects = ko.observableArray()
    @build_state = ko.observable()
    @org = ko.observable()
    @users = ko.observable()
    @refreshing_projects = ko.observable(false)
    @projects_have_been_loaded = ko.observable(false)
    @build_has_been_loaded = ko.observable(false)
    @builds_have_been_loaded = ko.observable(false)

    @org_has_been_loaded = @komp =>
      # TODO: extract the billing portion from the project/users portion
      @org() && @org().loaded()

    @current_page = ko.observable(new CI.inner.Page)

    @favicon = new CI.inner.Favicon(@current_page)

    @billing = ko.observable(new CI.inner.Billing)

    @dashboard_ready = @komp =>
      @projects_have_been_loaded() and @builds_have_been_loaded()

    # user is looking at the project's summary, but hasn't followed it
    @show_follow_project_button = @komp =>
      @project() &&
       @project().loaded_settings() &&
        !@project().followed() &&
         @project().project_name() is @current_page().project_name

    if window.renderContext.current_user
      @current_user = ko.observable(new CI.inner.User window.renderContext.current_user)
      @pusher = new CI.Pusher @current_user().login
      mixpanel.name_tag(@current_user().login)
      mixpanel.identify(@current_user().login)
      _rollbarParams.person = {id: @current_user().login}

    @logged_in = @komp =>
      @current_user?()

    @intercomUserLink = @komp =>
      @build() and @build() and @projects() # make it update each time the URL changes
      path = window.location.pathname.match("/gh/([^/]+/[^/]+)")
      if path
        "https://www.intercom.io/apps/vnk4oztr/users" +
          "?utf8=%E2%9C%93" +
          "&filters%5B0%5D%5Battr%5D=custom_data.pr-followed" +
          "&filters%5B0%5D%5Bcomparison%5D=contains&filters%5B0%5D%5Bvalue%5D=" +
          path[1]

  refreshBuildState: () =>
    VM.loadProjects()
    VM.current_page().refresh()

  loadProjects: () =>
    $.getJSON '/api/v1/projects', (data) =>
      projects = (new CI.inner.Project d for d in data)
      projects.sort CI.inner.Project.sidebarSort
      @projects(projects)
      @projects_have_been_loaded(true)

  followed_projects: () => @komp =>
    (p for p in @projects() when p.followed())

  has_followed_projects: () => @komp =>
    @followed_projects()().length > 0

  has_no_followed_projects: () => @komp =>
    @followed_projects()().length == 0

  show_add_projects: () => @komp =>
    @has_no_followed_projects()() && _.isEmpty(@builds())

  show_build_table: () => @komp =>
    @has_followed_projects()() || not _.isEmpty(@builds())

  refresh_project_src: () => @komp =>
    if @refreshing_projects()
      "/img/ajax-loader.gif"
    else
      "/img/arrow_refresh.png"

  loadDashboard: (cx) =>
    @loadProjects()
    @loadRecentBuilds()
    if window._gaq? # we dont use ga in test mode
      _gaq.push(['_trackPageview', '/dashboard'])
    mixpanel.track("Dashboard")
    display "dashboard"

  loadAddProjects: (cx) =>
    @current_user().loadOrganizations()
    @current_user().loadCollaboratorAccounts()
    display "add_projects", {}
    if @current_user().repos().length == 0
      track_signup_conversion()

  loadBuilds: (path, refresh) =>
    @cleanObjs(@builds())

    if not refresh
      @builds.removeAll()
      @builds_have_been_loaded(false)

    $.getJSON path, (data) =>
      @builds((new CI.inner.Build d for d in data))
      @builds_have_been_loaded(true)

  loadRecentBuilds: (refresh) =>
    @loadBuilds('/api/v1/recent-builds', refresh)

  loadOrg: (username, refresh) =>
    if !@projects_have_been_loaded() then @loadProjects()

    @loadBuilds("/api/v1/organization/#{username}", refresh)

    if not refresh
      display "dashboard",
        builds_table: 'org'

  loadProject: (username, project, branch, refresh) =>
    if !@projects_have_been_loaded() then @loadProjects()

    project_name = "#{username}/#{project}"
    path = "/api/v1/project/#{project_name}"
    path += "/tree/#{encodeURIComponent(branch)}" if branch?

    @loadBuilds(path, refresh)

    @maybeLoadProjectDetails(project_name)

    if not refresh
      display "dashboard",
        builds_table: 'project'

  loadEditOrgPage: (username, [_, subpage]) =>
    subpage or= "projects"

    if !@org() or (@org().name() isnt username)
      @org().clean() if @org()
      @org(null)
      @org new CI.inner.Org
        name: username
        subpage: subpage
      @org().loadSettings()
      mixpanel.track("View Org", {"username": username})
    else
      @org().subpage(subpage)

    display 'org_settings'

  # loads info needed to show plan info/trial notices and
  # if the user is following
  maybeLoadProjectDetails: (project_name) =>
    unless (@project() and @project().project_name() is project_name)
      @project().clean() if @project()
      @project new CI.inner.Project
        vcs_url: "https://github.com/#{project_name}"

    @project().maybe_load_settings()
    @project().maybe_load_billing()


  loadBuild: (cx, username, project, build_num) =>
    @build_has_been_loaded(false)
    project_name = "#{username}/#{project}"
    @maybeLoadProjectDetails(project_name)
    @build().clean() if @build()
    @build(null)
    $.getJSON "/api/v1/project/#{project_name}/#{build_num}", (data) =>
      @build(new CI.inner.Build data)
      @build_has_been_loaded(true)
      @build().maybeSubscribe()

      mixpanel_data =
        "running": not @build().stop_time()?
        "build-num": @build().build_num
        "vcs-url": @build().project_name()
        "outcome": @build().outcome()

      if @build().stop_time()?
        mixpanel_data.elapsed_hours = (Date.now() - new Date(@build().stop_time()).getTime()) / (60 * 60 * 1000)

      mixpanel.track("View Build", mixpanel_data)

    display "build", {project: project_name, build_num: build_num}

  loadExtraEditPageData: (subpage) =>
    if subpage is "parallel_builds"
      @project().load_billing()
    else if subpage is "api"
      @project().load_tokens()
    else if subpage is "env_vars"
      @project().load_env_vars()

  loadEditPage: (cx, username, project, [_, subpage]) =>
    subpage or= "settings"

    project_name = "#{username}/#{project}"

    # if we're already on this page, dont reload
    if !@project() or (@project().project_name() isnt project_name)
      if @project() then @project().clean()
      @project new CI.inner.Project
        vcs_url: "https://github.com/#{project_name}"

    @project().maybe_load_settings()
    VM.loadExtraEditPageData subpage

    display "edit", {project: project_name}, subpage

  loadAccountPage: (cx, [subpage, hash]) =>
    subpage or= "notifications"
    hash or= "meta"

    if subpage.indexOf("plans") == 0
      @current_user().loadOrganizations()

    if subpage.indexOf("notifications") == 0
      @current_user().syncGithub()
    else if subpage is "api"
      @current_user().load_tokens()

    display "account", {}, subpage, hash


  renderAdminPage: (subpage) =>
    display "admin", {}, subpage

  loadAdminPage: (cx) =>
    @renderAdminPage ""

  loadAdminUsers: (cx) =>
    $.getJSON "/api/v1/admin/users", (data) =>
      @users(data)
    @renderAdminPage "users"

  loadAdminBuildState: () =>
    $.getJSON '/api/v1/admin/build-state', (data) =>
      @build_state(data)
    @renderAdminPage "build_state"

  loadAdminProjects: (cx) =>
    $.getJSON '/api/v1/admin/projects', (data) =>
      data = (new CI.inner.Project d for d in data)
      @projects(data)
    @renderAdminPage "projects"

  loadAdminRecentBuilds: (refresh) =>
    @loadBuilds '/api/v1/admin/recent-builds', refresh
    if not refresh
      @renderAdminPage "recent_builds"

  refreshAdminRecentBuilds: () =>
    @loadAdminRecentBuilds(true)

  adminRefreshIntercomData: (data, event) =>
    $.ajax(
      url: "/api/v1/admin/refresh-intercom-data"
      type: "POST"
      event: event
    )
    false

  loadRootPage: (cx) =>
    if @logged_in()
      VM.loadDashboard cx
    else
      VM.home.display cx


window.VM = new CI.inner.CircleViewModel()
window.SammyApp = Sammy 'body', (n) ->
  @bind 'run-route', (e, data) ->
    VM.clearErrorMessage()
    mixpanel.track_pageview(data.path)
    if window._gaq? # we dont use ga in test mode
      window._gaq.push data.path


  # ignore forms with method ko, useful when using the knockout submit binding
  @route 'ko', '.*', ->
    false

  @before '/.*', (cx) -> VM.maybeRouteErrorPage(cx)
  @get '^/tests/inner', (cx) ->
    # do nothing, tests will load later

  @get '^/', (cx) =>
    VM.current_page new CI.inner.DashboardPage

    VM.loadRootPage(cx)

  @get '^/add-projects', (cx) => VM.loadAddProjects cx

  # before any project pages so that it gets routed first
  @get '^/gh/organizations/:username/settings(.*)', (cx) ->
    VM.current_page new CI.inner.OrgSettingsPage
      org_name: cx.params.username

    VM.loadEditOrgPage cx.params.username, splitSplat(cx)

  route_org_dashboard = (cx) ->
    VM.current_page new CI.inner.OrgDashboardPage
      username: cx.params.username

    VM.loadOrg cx.params.username

  # before any project pages so that it gets routed first
  @get '^/gh/organizations/:username', route_org_dashboard
  @get '^/gh/:username', route_org_dashboard

  @get '^/gh/:username/:project/edit(.*)',
    (cx) ->
      VM.current_page new CI.inner.ProjectSettingsPage
        username: cx.params.username
        project: cx.params.project

      VM.loadEditPage cx, cx.params.username, cx.params.project, splitSplat(cx)

  @get '^/account(.*)',
    (cx) ->
      VM.current_page new CI.inner.AccountPage
        title: "Account"

      VM.loadAccountPage cx, splitSplat(cx)

  @get '^/gh/:username/:project/tree/(.*)',
    (cx) ->
      # github allows '/' is branch names, so match more broadly and combine them
      branch = cx.params.splat.join('/')

      VM.current_page new CI.inner.ProjectBranchPage
        username: cx.params.username
        project: cx.params.project
        branch: branch

      VM.loadProject cx.params.username, cx.params.project, branch

  @get '^/gh/:username/:project/:build_num',
    (cx) ->
      VM.current_page new CI.inner.BuildPage
        username: cx.params.username
        project: cx.params.project
        build_num: cx.params.build_num

      VM.loadBuild cx, cx.params.username, cx.params.project, cx.params.build_num

  @get '^/gh/:username/:project',
    (cx) ->
      VM.current_page new CI.inner.ProjectPage
        username: cx.params.username
        project: cx.params.project

      VM.loadProject cx.params.username, cx.params.project

  @get '^/logout', (cx) -> VM.logout cx

  @get '^/admin', (cx) -> VM.loadAdminPage cx
  @get '^/admin/users', (cx) -> VM.loadAdminUsers cx
  @get '^/admin/projects', (cx) -> VM.loadAdminProjects cx
  @get '^/admin/build-state', (cx) -> VM.loadAdminBuildState cx
  @get '^/admin/recent-builds', (cx) ->
    VM.loadAdminRecentBuilds()
    VM.current_page new CI.inner.AdminRecentBuildsPage
      title: "Admin Recent Builds"

  # outer
  @get "^/docs(.*)", (cx) => VM.docs.display(cx)
  @get "^/about.*", (cx) => VM.about.display(cx)
  @get "^/privacy.*", (cx) => VM.privacy.display(cx)
  @get "^/jobs.*", (cx) => VM.jobs.display(cx)
  @get "^/enterprise.*", (cx) => VM.enterprise.display(cx)
  # @get "^/contact.*", (cx) => VM.contact.display(cx)
  @get "^/security", (cx) => VM.security.display(cx)

  @get "^/pricing.*", (cx) =>
    # the pricing page has broken links if served from outer to a logged-in user;
    # force them to inner.
    if VM.logged_in()
      return cx.redirect "/account/plans"
    else
      mixpanel.register_once {"view-pricing": true}

    # TODO: move this out of billing somehow
    VM.billing().loadPlans()
    VM.billing().loadPlanFeatures()
    VM.pricing.display(cx)


  @post "^/heroku/resources", -> true

  @get '^/api/.*', (cx) => false

  @get '^/gh/.*/artifacts/.*', (cx) =>
    # escape from sammy!
    location.assign cx.path

  @get '^(.*)', (cx) => VM.error.display(cx)

  # valid posts, allow to propegate
  @post '^/logout', -> true
  @post '^/admin/switch-user', -> true
  @post "^/about/contact", -> true

  @post '^/circumvent-sammy', (cx) -> true # dont show an error when posting

  @bind 'error', (e, data) ->
    if data? and data.error? and window.Airbrake?
      window.notifyError data


$(document).ready () ->
  path = window.location.pathname
  path = path.replace(/\/$/, '') # remove trailing slash
  path or= "/"

  if window.circleEnvironment is 'development'
    CI.maybeOverrideABTests(window.location.search, VM.ab)

  SammyApp.run path + window.location.search
