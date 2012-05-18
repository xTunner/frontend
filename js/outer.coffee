circle = $.sammy("body", ->

  # Page
  class Page
    constructor: (@name, @title) ->

    init: ->
      document.title = "Circle - " + @title

      # Render content
      @render()

      # Land at the right anchor on the page
      @scroll(window.location.hash)

      # Fetch page-specific libraries
      @lib() if @lib?

    render: ->
      $("body").attr("id","#{@name}-page").html HAML['header'](renderContext)
      $("body").append HAML[@name](renderContext)
      $("body").append HAML['footer'](renderContext)

    load: ->
      $.getScript("/assets/views/outer/#{@name}/#{@name}.hamlc", => @init())

    scroll: (hash) ->
      if hash == '' or hash == '#' then hash = "body"
      $('html, body').animate({scrollTop: $(hash).offset().top}, 0)

    display: ->
      if HAML? and HAML[@name]?
        @init()
      else
        @load()

  # Doc
  class Doc extends Page
    load: ->
      if !HAML['categories']
        $.getScript("/assets/views/outer/docs/categories.hamlc")

    article: ->
      $.getScript("/assets/views/outer/docs/category/#{@name}.hamlc", => @init())

  # Pages
  home = new Page("home", "Continuous Integration made easy")
  about = new Page("about", "About Us")
  privacy = new Page("privacy", "Privacy and Security")
  docs = new Page("docs", "Documentation")

  # Docs
  article = new Doc("article", "Sample template for an Article")

  # Per-Page Libs
  home.lib = placeholder
  about.lib = placeholder
  article.lib = syntaxhighlight

  placeholder = ->
    if !Modernizr.input.placeholder
      $.getScript("/assets/js/vendor/jquery.placeholder.js", ->
        $("input, textarea").placeholder()
      )

  syntaxhighlight = ->
    $.getScript("/assets/js/vendor/highlight.pack.js", ->
      $("pre code").each (i, e) -> hljs.highlightBlock e
    )

  # Google analytics
  @bind 'event-context-after', ->
    if window._gaq? # we dont use ga in test mode
      window._gaq.push @path

  # Airbrake
  @bind 'error', (e, data) ->
    if data? and data.error? and window.Hoptoad?
      window.Hoptoad.notify data.error

  # Navigation
  @get "/", (context) -> home.display()
  @get "/about.*", (context) -> about.display()
  @get "/privacy.*", (context) -> privacy.display()
  @get "/docs", (context) -> docs.display()

  # Documentation
  @get "/docs/article.*", -> article.display()
)

# Global polyfills
if $.browser.msie and $.browser.version > 6 and $.browser.version < 9
  $.getScript("/assets/js/vendor/selectivizr-1.0.2.js")

# Run the application
$ -> circle.run window.location.pathname
