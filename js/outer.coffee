circle = $.sammy("body", ->

  # Page
  class Page
    constructor: (@name, @title) ->

    render: ->
      document.title = "Circle - " + @title

      # Render content
      $("body").attr("id","#{@name}-page").html HAML['header'](renderContext)
      $("body").append HAML[@name](renderContext)
      $("body").append HAML['footer'](renderContext)

      # Sammy eats hashes, so we need to reapply it to land at the right anchor on the page
      @scroll(window.location.hash)

      # Apply polyfill(s) if they exists
      @polyfill() if @polyfill?

    load: ->
      $.getScript("/assets/views/outer/#{@name}/#{@name}.hamlc", => @render())

    scroll: (hash) ->
      if hash == '' or hash == '#' then hash = "body"
      $('html, body').animate({scrollTop: $(hash).offset().top}, 0);

    display: ->
      if HAML? and HAML[@name]?
        @render()
      else
        @load()

  # Pages
  home = new Page("home", "Continuous Integration made easy")
  about = new Page("about", "About Us")
  privacy = new Page("privacy", "Privacy Policy")

  # Per-Page Polyfills
  polyfill = ->
    if !Modernizr.input.placeholder
      $.getScript("/assets/js/vendor/jquery.placeholder.js", ->
        $("input, textarea").placeholder()
      )
  about.polyfill = polyfill
  home.polyfill = polyfill

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
)

# Global polyfills
if $.browser.msie and $.browser.version > 6 and $.browser.version < 9
  $.getScript("/assets/js/vendor/selectivizr-1.0.2.js")

# Run the application
$ -> circle.run window.location.pathname
