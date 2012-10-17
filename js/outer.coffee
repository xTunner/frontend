queryParams = () ->
  res = {}
  params = window.location.search.substring(1).split("&")
  for p in params
    [k,v] = p.split("=")
    res[k] = v
  res

window.queryParams = queryParams

circle = $.sammy "body", ->

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
      abTimeoutFn = ->
        renderContext.titleText = "Continuous Integration &nbsp; for Web Apps"
        $("#mainHeading").css("display", "")

      abtimeout = setTimeout(abTimeoutFn, 500)

      _kmq.push(['trackClickOnOutboundLink', '.kissAuthGithub', 'join link clicked'])
      _kmq.push(['trackSubmit', '#beta', 'beta form submitted'])
      _kmq.push( () ->
        titleText = KM.ab("headerText", ["Continuous Integration &nbsp; for Web Apps", "Fast, Easy Continuous&nbsp;Integration"])
        $("#mainHeading").html(titleText)
        $("#mainHeading").css("display", "")
        clearTimeout(abtimeout))


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
        $("body").attr("id","#{@name}-page").html(HAML['header'](renderContext))
        $("body").append(HAML['title'](renderContext))
        $("#title h1").text("Documentation")
        $("body").append("<div id='content'><section class='article'></section></div>")
        $(".article").append(HAML['categories']({categories: @categories(), page: name})).append(HAML[name](renderContext))
        $("body").append(HAML['footer'](renderContext))


  # Pages
  home = new Home("home", "Continuous Integration made easy")
  about = new Page("about", "About Us")
  privacy = new Page("privacy", "Privacy and Security")
  docs = new Docs("docs", "Documentation")

  # Per-Page Libs
  highlight = =>
    if !hljs?
      $.getScript "/assets/js/vendor/highlight.pack.js", =>
        $("pre code").each (i, e) => hljs.highlightBlock e

    else
      $("pre code").each (i, e) => hljs.highlightBlock e

  placeholder = =>
    if !Modernizr.input.placeholder
      $.getScript "/assets/js/vendor/jquery.placeholder.js", =>
        $("input, textarea").placeholder()

  home.lib = =>
    trigger = =>
      $("#testimonials").waypoint ((event, direction) ->
        $("#testimonials").addClass("scrolled")
      ),
        offset: "80%"
    trigger()
    placeholder()

  docs.lib = =>
    sidebar = =>
      $("ul.topics").stickyMojo
        footerID: "#footer"
        contentID: ".article article"
    highlight()
    sidebar()

  about.lib = =>
    placeholder()


  # Google analytics
  @bind 'event-context-after', ->
    if window._gaq? # we dont use ga in test mode
      window._gaq.push @path

  # Airbrake
  @bind 'error', (e, data) ->
    if data? and data.error? and window.Hoptoad?
      window.Hoptoad.notify data.error

  # Kissmetrics
  if renderContext.showJoinLink
    _kmq.push(['record', "showed join link"])

  # Navigation
  @get "/docs(.*)", (cx) -> docs.display(cx)
  @get "/about.*", (cx) -> about.display(cx)
  @get "/privacy.*", (cx) -> privacy.display(cx)
  @get "/", (cx) -> home.display(cx)
  @get("/gh/.*", (cx) =>
    @unload()
    window.location = cx.path)
  @post "/notify", -> true # allow to propagate
  @post "/about/contact", -> true # allow to propagate

# Global polyfills
if $.browser.msie and $.browser.version > 6 and $.browser.version < 9
  $.getScript("/assets/js/vendor/selectivizr-1.0.2.js")
`!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0];if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src="//platform.twitter.com/widgets.js";fjs.parentNode.insertBefore(js,fjs);}}(document,"script","twitter-wjs");`

# Run the application
$ -> circle.run window.location.pathname.replace(/\/$/, '')
