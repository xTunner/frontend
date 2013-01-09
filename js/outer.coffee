class OuterViewModel
  constructor: ->
    @ab = (new CI.ABTests(ab_test_definitions)).ab_tests
    @home = new CI.outer.Home("home", "Continuous Integration made easy")
    @about = new CI.outer.Page("about", "About Us")
    @privacy = new CI.outer.Page("privacy", "Privacy and Security")
    @pricing = new CI.outer.Pricing("pricing", "Plans and Pricing")
    @docs = new CI.outer.Docs("docs", "Documentation")
    @error = new CI.outer.Error("error", "Error")

  setErrorMessage: =>
    # do nothing

window.VM = new OuterViewModel

SammyApp = $.sammy "body", ->

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
  @get "/docs(.*)", (cx) -> VM.docs.display(cx)
  @get "/about.*", (cx) -> VM.about.display(cx)
  @get "/privacy.*", (cx) -> VM.privacy.display(cx)
  @get "/pricing.*", (cx) -> VM.pricing.display(cx)
  @get "/", (cx) -> VM.home.display(cx)
  @post "/notify", -> true # allow to propagate
  @post "/about/contact", -> true # allow to propagate
  @get "/.*", (cx) -> # catch-all for error pages
    if renderContext.status
      VM.error.display(cx)


# Run the application
$ ->
  SammyApp.run window.location.pathname.replace(/\/$/, '')
