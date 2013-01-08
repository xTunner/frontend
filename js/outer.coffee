class OuterViewModel
  constructor: ->
    @ab = (new CI.ABTests(ab_test_definitions)).ab_tests

window.OuterVM = new OuterViewModel

SammyApp = $.sammy "body", ->

  # Page
  class Page
    constructor: (@name, @title) ->

    display: (cx) =>
      document.title = "Circle - " + @title

      # Render content
      @render(cx)

      # Land at the right anchor on the page
      @scroll window.location.hash

      # Fetch page-specific libraries
      @lib() if @lib?

      ko.applyBindings(OuterVM)

    render: (cx) =>
      $("body").attr("id","#{@name}-page").html HAML['header'](renderContext)
      $("body").append HAML[@name](renderContext)
      $("body").append HAML['footer'](renderContext)

    scroll: (hash) =>
      if hash == '' or hash == '#' then hash = "body"
      $('html, body').animate({scrollTop: $(hash).offset().top}, 0)


  class Home extends Page
    render: (cx) =>
      super(cx)
      _kmq.push(['trackClickOnOutboundLink', '#join', 'hero join link clicked'])
      _kmq.push(['trackClickOnOutboundLink', '.kissAuthGithub', 'join link clicked'])
      _kmq.push(['trackClickOnOutboundLink', '#second-join', 'footer join link clicked'])
      _kmq.push(['trackSubmit', '#beta', 'beta form submitted'])
      _gaq.push(['_trackPageview', '/homepage'])

  # Doc
  class Docs extends Page
    filename: (cx) =>
      name = cx.params.splat[0]
      if name
        name.replace('/', '').replace('-', '_').replace(/#.*/, '')
      else
        "docs"

    categories: (cx) =>
      # build a table of contents dynamically from all the pages. DRY.
      pages = [
                "getting-started",
                "manually",
                "common-problems",
                "configuration",
                "config-sample",
                "environment",
                "faq",
# "notifications",
#                "api"
              ]
      categories = {}
      for p in pages
        slug = p.replace("-", "_")
        template = HAML[slug]()
        node = $(template)
        title = node.find('.title > h1').text().trim()
        subtitle = node.find('.title > h4').text().trim()
        icon = node.find('.title > h1 > i').attr('class')
        section_nodes = node.find('.doc > .section > a')
        sections = []
        for s in section_nodes
          sections.push
            title: $(s).text().trim()
            hash: $(s).attr("id")
        categories[p] =
          url: "/docs/#{p}"
          slug: slug
          title: title
          subtitle: subtitle
          icon: icon
          sections: sections
      categories


    render: (cx) =>
      name = @filename cx
      if name == 'docs'
        $("body").attr("id","docs-page").html HAML['header'](renderContext)
        $("body").append HAML['docs']({categories: @categories()})
        $("body").append HAML['footer'](renderContext)
      else
        $("body").attr("id","docs-page").html(HAML['header'](renderContext))
        $("body").append(HAML['title'](renderContext))
        $("#title h1").text("Documentation")
        $("body").append("<div id='content'><section class='article'></section></div>")
        $(".article").append(HAML['categories']({categories: @categories(), page: name})).append(HAML[name](renderContext))
        $("body").append(HAML['footer'](renderContext))


  # Pages
  home = new Home("home", "Continuous Integration made easy")
  about = new Page("about", "About Us")
  privacy = new Page("privacy", "Privacy and Security")
  pricing = new Page("pricing", "Plans and Pricing")
  docs = new Docs("docs", "Documentation")

  # Define Libs
  highlight = =>
    # Not happy with how this looks (esp since it doesnt support yaml). Rethinking.
    #$("pre code").each (i, e) =>
    #  hljs.highlightBlock e

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
    trigger = =>
      $("#testimonials").waypoint ((event, direction) ->
        $("#testimonials").addClass("scrolled")
      ),
        offset: "80%"
    trigger()
    placeholder()
    follow()

  docs.lib = =>
    follow()
    sidebar = =>
      $("ul.topics").stickyMojo
        footerID: "#footer"
        contentID: ".article article"
    highlight()
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


  # Twitter Follow

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
      error = renderContext.status
      url = renderContext.githubPrivateAuthURL
      titles =
        401: "Login required"
        404: "Page not found"
        500: "Internal server error"

      messages =
        401: "<a href=\"#{url}\">You must <b>log in</b> to view this page.</a>"
        404: "We're sorry, but that page doesn't exist."
        500: "We're sorry, but something broke."

      title = titles[error] or "Something unexpected happened"
      message = messages[error] or "Something completely unexpected happened"

      # Set the title
      document.title = "Circle - " + title

      # Display page
      $("body").attr("id","error").html HAML['header'](renderContext)
      $("body").append HAML['error'](title: title, error: renderContext.status, message: message)
      $('body > div').wrapAll('<div id="wrap"/>');
      $("body").append HAML['footer'](renderContext)


# Run the application
$ ->
  SammyApp.run window.location.pathname.replace(/\/$/, '')
