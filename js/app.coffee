noop = () ->
  null

CI.ajax.init()

setOuter = =>
  $('html').removeClass('outer').addClass('inner')
display = (template, args) ->
  setOuter()
  $('#main').html(HAML[template](args))
  ko.applyBindings(VM)


class CircleViewModel extends CI.inner.Obj
  constructor: ->
    @ab = (new CI.ABTests(ab_test_definitions)).ab_tests
    @error_message = ko.observable(null)
    @turbo_mode = ko.observable(false)
    @from_heroku = ko.observable(window.renderContext.from_heroku)
    @flash = ko.observable(@flash)

    # inner
    @build = ko.observable()
    @builds = ko.observableArray()
    @project = ko.observable()
    @projects = ko.observableArray()
    @recent_builds = ko.observableArray()
    @build_state = ko.observable()
    @admin = ko.observable()
    @refreshing_projects = ko.observable(false)
    @projects_have_been_loaded = ko.observable(false)
    @build_has_been_loaded = ko.observable(false)
    @recent_builds_have_been_loaded = ko.observable(false)
    @project_builds_have_been_loaded = ko.observable(false)
    @dashboard_ready = @komp =>
      @projects_have_been_loaded() and @recent_builds_have_been_loaded()
    @selected = ko.observable({}) # Tracks what the dashboard is showing
    @billing = ko.observable(new CI.inner.Billing)

    if window.renderContext.current_user
      try
        olark 'api.box.hide'
      catch error
        console.error 'Tried to hide olark, but it threw:', error
      @current_user = ko.observable(new CI.inner.User window.renderContext.current_user)
      @pusher = new CI.Pusher @current_user().login
      _kmq.push ['identify', @current_user().login]
      mixpanel.name_tag(@current_user().login)
      mixpanel.identify(@current_user().login)


    @intercomUserLink = @komp =>
      @build() and @build() and @projects() # make it update each time the URL changes
      path = window.location.pathname.match("/gh/([^/]+/[^/]+)")
      if path
        "https://www.intercom.io/apps/vnk4oztr/users" +
          "?utf8=%E2%9C%93" +
          "&filters%5B0%5D%5Battr%5D=custom_data.pr-followed" +
          "&filters%5B0%5D%5Bcomparison%5D=contains&filters%5B0%5D%5Bvalue%5D=" +
          path[1]

    # outer
    @home = new CI.outer.Home("home", "Continuous Integration and Deployment")
    @about = new CI.outer.About("about", "About Us")
    @pricing = new CI.outer.Page("pricing", "Plans and Pricing")
    @docs = new CI.outer.Docs("docs", "Documentation")
    @error = new CI.outer.Error("error", "Error")

    @jobs = new CI.outer.Page("jobs", "Work at CircleCI")
    @privacy = new CI.outer.Page("privacy", "Privacy and Security")

    @query_results_query = ko.observable(null)
    @query_results = ko.observableArray([])

  authGitHubSlideDown: =>
    mixpanel.track("Auth GitHub Modal Why Necessary")
    $(".why_authenticate_github_modal").slideDown()

  # Project dashboard will eventually be merged into regular dashboard,
  # and this name will be more correct
  refreshDashboard: () =>
    VM.loadProjects()
    sel = VM.selected()
    if sel.admin_builds
      VM.refreshAdminRecentBuilds()
    else if sel.project_name
      VM.loadProject(sel.username, sel.project, sel.branch, true)
    else
      VM.loadRecentBuilds()

  performDocSearch: (query) =>
    $.ajax
      url: "/search-articles"
      type: "GET"
      data:
        query: query
      success: (results) =>
        window.SammyApp.setLocation("/docs")
        @query_results results.results
        @query_results_query results.query
    query

  searchArticles: (form) =>
    @performDocSearch($(form).find('.search-query').val())
    return false

  suggestArticles: (query, process) =>
    $.ajax
      url: "/autocomplete-articles"
      type: "GET"
      data:
        query: query
      success: (autocomplete) =>
        process autocomplete.suggestions
    null

  testCall: (arg) =>
    alert(arg)

  clearErrorMessage: () =>
    @error_message null

  setErrorMessage: (message) =>
    if message == "" or not message?
      message = "Unknown error"
    if message.slice(-1) != '.'
      message += '.'
    @error_message message
    $('html, body').animate({ scrollTop: 0 }, 0);

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

  refresh_project_src: () => @komp =>
    if @refreshing_projects()
      "/img/ajax-loader.gif"
    else
      "/img/arrow_refresh.png"

  loadRecentBuilds: () =>
    $.getJSON '/api/v1/recent-builds', (data) =>
      @recent_builds((new CI.inner.Build d for d in data))
      @recent_builds_have_been_loaded(true)

  loadDashboard: (cx) =>
    @loadProjects()
    @loadRecentBuilds()
    if window._gaq? # we dont use ga in test mode
      _gaq.push(['_trackPageview', '/dashboard'])
    mixpanel.track("Dashboard")
    display "dashboard", {}


  loadAddProjects: (cx) =>
    @current_user().loadOrganizations()
    @current_user().loadCollaboratorAccounts()
    display "add_projects", {}
    if @current_user().repos().length == 0
      track_signup_conversion()



  loadProject: (username, project, branch, refresh) =>
    if @projects().length is 0 then @loadProjects()

    project_name = "#{username}/#{project}"
    path = "/api/v1/project/#{project_name}"
    path += "/tree/#{encodeURIComponent(branch)}" if branch?

    if not refresh
      @builds.removeAll()
      @project_builds_have_been_loaded(false)

    $.getJSON path, (data) =>
      @builds((new CI.inner.Build d for d in data))
      @project_builds_have_been_loaded(true)

    if not refresh
      display "project",
        project: project_name
        branch: branch


  loadBuild: (cx, username, project, build_num) =>
    @build_has_been_loaded(false)
    project_name = "#{username}/#{project}"
    @build(null)
    $.getJSON "/api/v1/project/#{project_name}/#{build_num}", (data) =>
      @build(new CI.inner.Build data)
      @build_has_been_loaded(true)
      @build().maybeSubscribe()
    display "build", {project: project_name, build_num: build_num}


  loadEditPage: (cx, username, project, subpage) =>
    project_name = "#{username}/#{project}"

    subpage = subpage[0].replace('#', '').replace('-', '_')
    subpage = subpage || "settings"

    # if we're already on this page, dont reload
    if (not @project() or
    (@project().vcs_url() isnt "https://github.com/#{project_name}"))
      $.getJSON "/api/v1/project/#{project_name}/settings", (data) =>
        @project(new CI.inner.Project data)
        @project().get_users()
        if subpage is "parallel_builds"
          @project().load_paying_user()
          @billing().load()

    else if subpage is "parallel_builds"
      @project().load_paying_user()
      @billing().load()

    setOuter()
    $('#main').html(HAML['edit']({project: project_name}))
    $('#subpage').html(HAML['edit_' + subpage]({}))
    ko.applyBindings(VM)


  loadAccountPage: (cx, subpage) =>
    subpage = subpage[0].replace(/\//, '') # first one
    subpage = subpage.replace(/\//g, '_')
    subpage = subpage.replace(/-/g, '_')
    [subpage, hash] = subpage.split('#')
    subpage or= "notifications"
    hash or= "meta"

    if subpage.indexOf("plans") == 0
      @billing().load()

    if subpage.indexOf("notifications") == 0
      @current_user().syncGithub()

    setOuter()
    $('#main').html(HAML['account']({}))
    $('#subpage').html(HAML['account_' + subpage]({}))
    $("##{subpage}").addClass('active')
    if $('#hash').length
      $("##{hash}").addClass('active')
      $('#hash').html(HAML['account_' + subpage + "_" + hash]({}))
    ko.applyBindings(VM)


  renderAdminPage: (subpage) =>
    setOuter()
    $('#main').html(HAML['admin']({}))
    if subpage
      $('#subpage').html(HAML['admin_' + subpage]())
    ko.applyBindings(VM)


  loadAdminPage: (cx, subpage) =>
    if subpage
      subpage = subpage.replace('/', '')
      $.getJSON "/api/v1/admin/#{subpage}", (data) =>
        @admin(data)
    @renderAdminPage subpage

  loadAdminBuildState: () =>
    $.getJSON '/api/v1/admin/build-state', (data) =>
      @build_state(data)
    @renderAdminPage "build_state"


  loadAdminProjects: (cx) =>
    $.getJSON '/api/v1/admin/projects', (data) =>
      data = (new CI.inner.Project d for d in data)
      @projects(data)
    @renderAdminPage "projects"


  loadAdminRecentBuilds: () =>
    $.getJSON '/api/v1/admin/recent-builds', (data) =>
      @recent_builds((new CI.inner.Build d for d in data))
    @renderAdminPage "recent_builds"

  refreshAdminRecentBuilds: () =>
    $.getJSON '/api/v1/admin/recent-builds', (data) =>
      @recent_builds((new CI.inner.Build d for d in data))

  adminRefreshIntercomData: (data, event) =>
    $.ajax(
      url: "/api/v1/admin/refresh-intercom-data"
      type: "POST"
      event: event
    )
    false


  loadJasmineTests: (cx) =>
    $.getScript "/assets/js/tests/inner-tests.js.dieter"

  raiseIntercomDialog: (message) =>
    unless intercomJQuery?
      notifyError "Uh-oh, our Help system isn't available. Please email us instead, at <a href='mailto:sayhi@circleci.com'>sayhi@circleci.com</a>!"
      return

    jq = intercomJQuery
    jq("#IntercomTab").click()
    unless jq('#IntercomNewMessageContainer').is(':visible')
      jq('.new_message').click()
    jq('#newMessageBody').focus()
    if message
      jq('#newMessageBody').text(message)

  logout: (cx) =>
    # TODO: add CSRF protection
    $.post('/logout', () =>
       window.location = "/")

  unsupportedRoute: (cx) =>
    throw("Unsupported route: " + cx.params.splat)

  goDashboard: (data, event) =>
    # signature so this can be used as knockout click handler
    window.SammyApp.setLocation("/")

  # use in ko submit binding, expects button to submit form
  mockFormSubmit: (cb) =>
    (formEl) =>
      $formEl = $(formEl)
      $formEl.find('button').addClass 'disabled'
      if cb? then cb.call()
      false

  loadRootPage: (cx) =>
    if VM.current_user
      VM.loadDashboard cx
    else
      VM.home.display cx

  # For error pages, we are passed the status from the server, stored in renderContext.
  # Because that will remain true when we navigate in-app, we need to make all links cause
  # a page reload, by running SammyApp.unload(). However, the first time this is run is
  # actually before Sammy 0.7.2 loads the click handlers, so unload doesn't help. To combat
  # this, we disable sammy after a second, by which time the handlers must surely have run.
  maybeRouteErrorPage: (cx) =>
    if renderContext.status
      @error.display(cx)
      setInterval( =>
        window.SammyApp.unload()
      , 1000)
      return false

    return true

window.VM = new CircleViewModel()
window.SammyApp = Sammy 'body', (n) ->

    @bind 'run-route', (e, data) ->
      mixpanel.track_pageview(data.path)

    # ignore forms with method ko, useful when using the knockout submit binding
    @route 'ko', '.*', ->
      false

    @before '/.*', (cx) -> VM.maybeRouteErrorPage(cx)
    @get '^/tests/inner', (cx) -> VM.loadJasmineTests(cx)

    @get '^/', (cx) =>
      VM.selected({})
      VM.loadRootPage(cx)

    @get '^/add-projects', (cx) => VM.loadAddProjects cx
    @get '^/gh/:username/:project/edit(.*)',
      (cx) -> VM.loadEditPage cx, cx.params.username, cx.params.project, cx.params.splat
    @get '^/account(.*)',
      (cx) -> VM.loadAccountPage cx, cx.params.splat
    @get '^/gh/:username/:project/tree/(.*)',
      (cx) ->
        # github allows '/' is branch names, so match more broadly and combine them
        cx.params.branch = cx.params.splat.join('/')
        VM.selected
          username: cx.params.username
          project: cx.params.project
          project_name: "#{cx.params.username}/#{cx.params.project}"
          branch: cx.params.branch

        VM.loadProject cx.params.username, cx.params.project, cx.params.branch

    @get '^/gh/:username/:project/:build_num',
      (cx) -> VM.loadBuild cx, cx.params.username, cx.params.project, cx.params.build_num
    @get('^/gh/:username/:project',
      (cx) ->
        VM.selected
          username: cx.params.username
          project: cx.params.project
          project_name: "#{cx.params.username}/#{cx.params.project}"

        VM.loadProject cx.params.username, cx.params.project

    @get '^/logout', (cx) -> VM.logout cx

    @get '^/admin', (cx) -> VM.loadAdminPage cx
    @get '^/admin/users', (cx) -> VM.loadAdminPage cx, "users"
    @get '^/admin/projects', (cx) -> VM.loadAdminProjects cx)
    @get '^/admin/recent-builds', (cx) ->
      VM.loadAdminRecentBuilds cx
      VM.selected
        admin_builds: true

    @get '^/admin/build-state', (cx) -> VM.loadAdminBuildState cx

    # outer
    @get "^/docs(.*)", (cx) => VM.docs.display(cx)
    @get "^/about.*", (cx) => VM.about.display(cx)
    @get "^/privacy.*", (cx) => VM.privacy.display(cx)
    @get "^/jobs.*", (cx) => VM.jobs.display(cx)
    @get "^/pricing.*", (cx) =>
      VM.billing().loadPlans()
      VM.billing().loadPlanFeatures()
      VM.pricing.display(cx)

    @post "^/heroku/resources", -> true

    @get '^/api/.*', (cx) => false

    @get '^(.*)', (cx) => VM.error.display(cx)

    # valid posts, allow to propegate
    @post '^/logout', -> true
    @post '^/admin/switch-user', -> true
    @post "^/about/contact", -> true # allow to propagate

    @post '^/circumvent-sammy', (cx) -> true # dont show an error when posting

    # Google analytics
    @bind 'event-context-after', ->
      if window._gaq? # we dont use ga in test mode
        window._gaq.push @path

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
