CI.ajax.init()

display = (template, args, subpage, hash) ->
  $('html').removeClass('outer').removeClass('new-outer').addClass('inner')
  $('#main').html(HAML.header())
  $('#main').append(HAML[template](args))
  if subpage
    $('#subpage').html(HAML["#{template}_#{subpage}"](args))
    $("##{subpage}").addClass('active')
  if $('#hash').length
    $("##{hash}").addClass('active')
    $('#hash').html(HAML["#{template}_#{subpage}_#{hash}"](args))

  ko.applyBindings(VM)

splitSplat = (cx) ->
  p = cx.params.splat[0]
  p = p.replace(/-/g, '_').replace(/\//g, '')
  p.split('#')


class CI.inner.CircleViewModel extends CI.inner.Foundation

  constructor: ->
    super()
    @favicon = new CI.inner.Favicon(@selected)

    # outer
    @home = new CI.outer.Home("home", "Continuous Integration and Deployment")
    @about = new CI.outer.About("about", "About Us", "View About")
    @pricing = new CI.outer.Page("pricing", "Plans and Pricing", "View Pricing Outer")
    @docs = new CI.outer.Docs("docs", "Documentation", "View Docs")
    @error = new CI.outer.Error("error", "Error")

    @jobs = new CI.outer.Page("jobs", "Work at CircleCI")
    @privacy = new CI.outer.Page("privacy", "Privacy", "View Privacy")
#    @contact = new CI.outer.Page("contact", "Contact us", "View Contact")
#    @security = new CI.outer.Page("security", "Security", "View Security")

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
    @org_has_been_loaded = ko.observable(false)
    @builds_have_been_loaded = ko.observable(false)
    @navbar = ko.observable(new CI.inner.Navbar(@selected, @build))
    @billing = ko.observable(new CI.inner.Billing)


    @dashboard_ready = @komp =>
      @projects_have_been_loaded() and @builds_have_been_loaded()

    # user is looking at the project's summary, but hasn't followed it
    @show_follow_project_button = @komp =>
      @project() && !@project().followed() && @project().project_name() is @selected().project_name


    if window.renderContext.current_user
      CI.olark.disable()
      @current_user = ko.observable(new CI.inner.User window.renderContext.current_user)
      @pusher = new CI.Pusher @current_user().login
      mixpanel.name_tag(@current_user().login)
      mixpanel.identify(@current_user().login)
      _rollbarParams.person = {id: @current_user().login}


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
    VM.selected().refresh_fn() if VM.selected().refresh_fn

  # Keep this until backend has a chance to fully deploy
  refreshDashboard: () =>
    @refreshBuildState()

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
    display "dashboard",
      builds_table: 'user_builds_table'


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
    settings_path = path + "/settings"
    path += "/tree/#{encodeURIComponent(branch)}" if branch?

    @loadBuilds(path, refresh)

    $.getJSON settings_path, (data) =>
      @project(new CI.inner.Project data)

    if not refresh
      display "dashboard",
        builds_table: 'project'

  loadOrgSettings: (username, callback) =>
    $.getJSON "/api/v1/organization/#{username}/settings", (data) =>
      @org().clean() if @org()
      @org(new CI.inner.Org data)
      @org_has_been_loaded(true)
      mixpanel.track("View Org", {"username": username})
      if callback? then callback()

  loadEditOrgPage: (username, [_, subpage]) =>
    subpage or= "projects"

    subpage_callback = () => @org().subpage(subpage)

    if !@org() or (@org().name() isnt username)
      @org_has_been_loaded(false)
      @org().clean() if @org()
      @org(null)

      @loadOrgSettings(username, subpage_callback)
    else
      subpage_callback()

    display 'org_settings',
      subpage: subpage

  loadBuild: (cx, username, project, build_num) =>
    @build_has_been_loaded(false)
    project_name = "#{username}/#{project}"
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
      @project().load_paying_user()
      @project().load_billing()
      @billing().load()
    else if subpage is "api"
      @project().load_tokens()
    else if subpage is "env_vars"
      @project().load_env_vars()

  loadEditPage: (cx, username, project, [_, subpage]) =>
    subpage or= "settings"

    project_name = "#{username}/#{project}"

    # if we're already on this page, dont reload
    if (not @project() or
    (@project().vcs_url() isnt "https://github.com/#{project_name}"))
      $.getJSON "/api/v1/project/#{project_name}/settings", (data) =>
        @project(new CI.inner.Project data)
        @project().get_users()
        VM.loadExtraEditPageData subpage
    else
        VM.loadExtraEditPageData subpage

    display "edit", {project: project_name}, subpage

  loadAccountPage: (cx, [subpage, hash]) =>
    subpage or= "notifications"
    hash or= "meta"

    if subpage.indexOf("plans") == 0
      @billing().load()

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
    if VM.current_user
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
    VM.selected
      refresh_fn: =>
        VM.loadRecentBuilds(true)
      mention_branch: true
      favicon_updator: VM.reset_favicon

    VM.loadRootPage(cx)

  @get '^/add-projects', (cx) => VM.loadAddProjects cx

  # before any project pages so that it gets routed first
  @get '^/gh/organizations/:username/settings(.*)', (cx) ->
    VM.selected
      page: "org_settings"
      crumbs: ['org', 'org_settings']
      username: cx.params.username
      favicon_updator: VM.reset_favicon

    VM.loadEditOrgPage cx.params.username, splitSplat(cx)

  route_org_dashboard = (cx) ->
    VM.selected
      page: "org"
      crumbs: ['org', 'org_settings']
      username: cx.params.username
      favicon_updator: VM.reset_favicon

    VM.loadOrg cx.params.username

  # before any project pages so that it gets routed first
  @get '^/gh/organizations/:username', route_org_dashboard
  @get '^/gh/:username', route_org_dashboard

  @get '^/gh/:username/:project/edit(.*)',
    (cx) ->
      VM.selected
        page: 'project_settings'
        crumbs: ['project', 'project_settings']
        username: cx.params.username
        project: cx.params.project
        project_name: "#{cx.params.username}/#{cx.params.project}"
        favicon_updator: VM.reset_favicon

      VM.loadEditPage cx, cx.params.username, cx.params.project, splitSplat(cx)

  @get '^/account(.*)',
    (cx) ->
      VM.selected
        page: "account"
        favicon_updator: VM.reset_favicon
      VM.loadAccountPage cx, splitSplat(cx)

  @get '^/gh/:username/:project/tree/(.*)',
    (cx) ->
      # github allows '/' is branch names, so match more broadly and combine them
      branch = cx.params.splat.join('/')

      VM.selected
        page: "branch"
        crumbs: ['project', 'branch', 'project_settings']
        username: cx.params.username
        project: cx.params.project
        project_name: "#{cx.params.username}/#{cx.params.project}"
        branch: branch
        mention_branch: false
        favicon_updator: VM.reset_favicon
        refresh_fn: =>
          VM.loadProject(cx.params.username, cx.params.project, branch, true)

      VM.loadProject cx.params.username, cx.params.project, branch

  @get '^/gh/:username/:project/:build_num',
    (cx) ->
      VM.selected
        page: "build"
        crumbs: ['project', 'branch', 'build', 'project_settings']
        username: cx.params.username
        project: cx.params.project
        project_name: "#{cx.params.username}/#{cx.params.project}"
        build_num: cx.params.build_num
        mention_branch: true
        refresh_fn: =>
          if VM.build() and VM.build().usage_queue_visible()
            VM.build().load_usage_queue_why()
        favicon_updator: =>
          VM.favicon.build_updator(VM.build())


      VM.loadBuild cx, cx.params.username, cx.params.project, cx.params.build_num

  @get '^/gh/:username/:project',
    (cx) ->
      VM.selected
        page: "project"
        crumbs: ['project', 'project_settings']
        username: cx.params.username
        project: cx.params.project
        project_name: "#{cx.params.username}/#{cx.params.project}"
        mention_branch: true
        favicon_updator: VM.reset_favicon
        refresh_fn: =>
          VM.loadProject(cx.params.username, cx.params.project, null, true)

      VM.loadProject cx.params.username, cx.params.project

  @get '^/logout', (cx) -> VM.logout cx

  @get '^/admin', (cx) -> VM.loadAdminPage cx
  @get '^/admin/users', (cx) -> VM.loadAdminUsers cx
  @get '^/admin/projects', (cx) -> VM.loadAdminProjects cx
  @get '^/admin/build-state', (cx) -> VM.loadAdminBuildState cx
  @get '^/admin/recent-builds', (cx) ->
    VM.loadAdminRecentBuilds()
    VM.selected
      page: "admin"
      admin_builds: true
      refresh_fn: VM.refreshAdminRecentBuilds
      favicon_updator: VM.reset_favicon

  # outer
  @get "^/docs(.*)", (cx) => VM.docs.display(cx)
  @get "^/about.*", (cx) => VM.about.display(cx)
  @get "^/privacy.*", (cx) => VM.privacy.display(cx)
  @get "^/jobs.*", (cx) => VM.jobs.display(cx)
  # @get "^/contact.*", (cx) => VM.contact.display(cx)
  # @get "^/security.*", (cx) => VM.security.display(cx)

  @get "^/pricing.*", (cx) =>
    # the pricing page has broken links if served from outer to a logged-in user;
    # force them to inner.
    if VM.current_user
      return cx.redirect "/account/plans"
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
