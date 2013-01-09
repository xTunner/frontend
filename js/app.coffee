CI.ajax.init()

display = (template, args) ->
  $('#main').html(HAML[template](args))
  ko.applyBindings(VM)


class CircleViewModel extends CI.inner.Obj
  constructor: ->
    @ab = (new CI.ABTests(ab_test_definitions)).ab_tests
    @error_message = ko.observable(null)

    # inner
    @build = ko.observable()
    @builds = ko.observableArray()
    @project = ko.observable()
    @projects = ko.observableArray()
    @recent_builds = ko.observableArray()
    @build_state = ko.observable()
    @admin = ko.observable()
    @refreshing_projects = ko.observable(false);

    if window.renderContext.current_user
      @billing = ko.observable(new CI.inner.Billing)
      @current_user = ko.observable(new CI.inner.User window.renderContext.current_user)
      @pusher = new CI.Pusher @current_user().login
      _kmq.push ['identify', @current_user().login]

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
    @home = new CI.outer.Home("home", "Continuous Integration made easy")
    @about = new CI.outer.Page("about", "About Us")
    @privacy = new CI.outer.Page("privacy", "Privacy and Security")
    @pricing = new CI.outer.Pricing("pricing", "Plans and Pricing")
    @docs = new CI.outer.Docs("docs", "Documentation")
    @error = new CI.outer.Error("error", "Error")


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

  available_projects: () => @komp =>
    (p for p in @projects() when not p.followed())

  followed_projects: () => @komp =>
    (p for p in @projects() when p.followed())

  has_followed_projects: () => @komp =>
    @followed_projects()().length > 0

  refresh_project_src: () => @komp =>
    if @refreshing_projects()
      "/img/ajax-loader.gif"
    else
      "/img/arrow_refresh.png"

  loadRecentBuilds: () =>
    $.getJSON '/api/v1/recent-builds', (data) =>
      @recent_builds((new CI.inner.Build d for d in data))

  loadDashboard: (cx) =>
    @loadProjects()
    @loadRecentBuilds()
    if window._gaq? # we dont use ga in test mode
      _gaq.push(['_trackPageview', '/dashboard'])
    display "dashboard", {}


  loadAddProjects: (cx) =>
    @current_user().loadOrganizations()
    @current_user().loadCollaboratorAccounts()
    display "add_projects", {}


  loadProject: (cx, username, project) =>
    project_name = "#{username}/#{project}"
    @builds.removeAll()
    $.getJSON "/api/v1/project/#{project_name}", (data) =>
      @builds((new CI.inner.Build d for d in data))
    display "project", {project: project_name}


  loadBuild: (cx, username, project, build_num) =>
    project_name = "#{username}/#{project}"
    @build(null)
    $.getJSON "/api/v1/project/#{project_name}/#{build_num}", (data) =>
      @build(new CI.inner.Build data)
      @build().maybeSubscribe()
    display "build", {project: project_name, build_num: build_num}


  loadEditPage: (cx, username, project, subpage) =>
    project_name = "#{username}/#{project}"

    subpage = subpage[0].replace('#', '')
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
    $('#main').html(HAML['account']({}))
    $('#subpage').html(HAML['account_' + subpage]({}))
    $("##{subpage}").addClass('active')
    if $('#hash').length
      $("##{hash}").addClass('active')
      $('#hash').html(HAML['account_' + subpage + "_" + hash]({}))
    ko.applyBindings(VM)


  renderAdminPage: (subpage) =>
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

  adminRefreshIntercomData: (data, event) =>
    $.ajax(
      url: "/api/v1/admin/refresh-intercom-data"
      type: "POST"
      event: event
    )
    false


  loadJasmineTests: (cx) =>
    # Run the tests within the local scope, so we can use the scope chain to
    # access classes and values throughout this file.
    window.TestTargets =
      log2: CI.math.log2
      Billing: CI.inner.Billing
      ansiToHtml: CI.terminal.ansiToHtml
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

window.VM = new CircleViewModel()
window.SammyApp = Sammy 'body', (n) ->
    @get('^/tests/inner', (cx) -> VM.loadJasmineTests(cx))

    if VM.current_user
      @get '^/', (cx) => VM.loadDashboard(cx)
    else
      @get "^/", (cx) => VM.home.display(cx)

    @get '^/add-projects', (cx) => VM.loadAddProjects cx
    @get '^/gh/:username/:project/edit(.*)',
      (cx) -> VM.loadEditPage cx, cx.params.username, cx.params.project, cx.params.splat
    @get '^/account(.*)',
      (cx) -> VM.loadAccountPage cx, cx.params.splat
    @get '^/gh/:username/:project/:build_num',
      (cx) -> VM.loadBuild cx, cx.params.username, cx.params.project, cx.params.build_num
    @get('^/gh/:username/:project',
      (cx) -> VM.loadProject cx, cx.params.username, cx.params.project

    @get '^/logout', (cx) -> VM.logout cx

    @get '^/admin', (cx) -> VM.loadAdminPage cx
    @get '^/admin/users', (cx) -> VM.loadAdminPage cx, "users"
    @get '^/admin/projects', (cx) -> VM.loadAdminProjects cx)
    @get '^/admin/recent-builds', (cx) -> VM.loadAdminRecentBuilds cx
    @get '^/admin/build-state', (cx) -> VM.loadAdminBuildState cx

    # outer
    @get "^/docs(.*)", (cx) => VM.docs.display(cx)
    @get "^/about.*", (cx) => VM.about.display(cx)
    @get "^/privacy.*", (cx) => VM.privacy.display(cx)
    @get "^/pricing.*", (cx) => VM.pricing.display(cx)

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
  SammyApp.run path
