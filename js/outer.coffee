class OuterViewModel
  constructor: ->
    @ab = (new CI.ABTests(ab_test_definitions)).ab_tests

window.OuterVM = new OuterViewModel

SammyApp = $.sammy "body", ->

  class Home extends CI.outer.Page
    render: (cx) =>
      super(cx)
      _kmq.push(['trackClickOnOutboundLink', '#join', 'hero join link clicked'])
      _kmq.push(['trackClickOnOutboundLink', '.kissAuthGithub', 'join link clicked'])
      _kmq.push(['trackClickOnOutboundLink', '#second-join', 'footer join link clicked'])
      _kmq.push(['trackSubmit', '#beta', 'beta form submitted'])
      _gaq.push(['_trackPageview', '/homepage'])


  # Pages
  home = new Home("home", "Continuous Integration made easy")
  about = new CI.outer.Page("about", "About Us")
  privacy = new CI.outer.Page("privacy", "Privacy and Security")
  pricing = new CI.outer.Page("pricing", "Plans and Pricing")
  docs = new CI.outer.Docs("docs", "Documentation")
  error = new CI.outer.Error("error", "Error")

  placeholder = =>
    $("input, textarea").placeholder()

  follow = =>
    $("#twitter-follow-template-div").empty()
    clone = $(".twitter-follow-template").clone()
    clone.removeAttr "style" # unhide the clone
    clone.attr "data-show-count", "false"
    clone.attr "class", "twitter-follow-button"
    $("#twitter-follow-template-div").append clone

    # reload twitter scripts to force them to run, converting a to iframe
    $.getScript "//platform.twitter.com/widgets.js"

  # Per-Page Libs
  home.lib = =>
    placeholder()
    follow()

  docs.lib = =>
    follow()
    sidebar = =>
      $("ul.topics").stickyMojo
        footerID: "#footer"
        contentID: ".article article"
    sidebar()

  about.lib = =>
    placeholder()
    follow()

  pricing.lib = =>
    $('html').popover
      html: true
      placement: "bottom"
      template: '<div class="popover billing-popover"><div class="popover-inner"><h3 class="popover-title"></h3><div class="popover-content"><p></p></div></div></div>'
      delay: 0
      trigger: "hover"
      selector: ".more-info"
    follow()

  error.lib = =>
    follow()


  # Google analytics
  @bind 'event-context-after', ->
    if window._gaq? # we dont use ga in test mode
      window._gaq.push @path

  # Airbrake
  @bind 'error', (e, data) ->
    if data? and data.error? and window.Airbrake?
      window.Airbrake.captureException data.error

  # Kissmetrics
  if renderContext.showJoinLink
    _kmq.push(['record', "showed join link"])


  # Navigation
  @get "/docs(.*)", (cx) -> docs.display(cx)
  @get "/about.*", (cx) -> about.display(cx)
  @get "/privacy.*", (cx) -> privacy.display(cx)
  @get "/pricing.*", (cx) -> pricing.display(cx)
  @get "/", (cx) -> home.display(cx)
  @post "/notify", -> true # allow to propagate
  @post "/about/contact", -> true # allow to propagate
  @get "/.*", (cx) -> # catch-all for error pages
    if renderContext.status
      error.display(cx)


# Run the application
$ ->
  SammyApp.run window.location.pathname.replace(/\/$/, '')
