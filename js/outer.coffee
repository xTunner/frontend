# Navigation
(($) ->
  circle = $.sammy("body", ->

    # Page
    class Page
      constructor: (@name, @title) ->

      render: ->
        document.title = "Circle - " + @title
        $('html, body').animate({ scrollTop: 0 }, 0);
        $("body").attr("id",@name).html HAML['header'](renderContext)
        $("body").append HAML[@name](renderContext)
        $("body").append HAML['footer'](renderContext)
        @polyfill() if @polyfill?

      load: (show) ->
        self = this
        $.getScript "assets/views/outer/#{@name}/#{@name}.hamlc", ->
          self.render() if show?

      display: ->
        if HAML? and HAML[@name]?
          @render()
        else
          @load(true)

    # Pages
    home = new Page("home", "Continuous Integration made easy")
    about = new Page("about", "About Us")
    privacy = new Page("privacy", "Privacy Policy")

    # Navigation
    @get "/", (context) -> home.display()
    @get "/about", (context) -> about.display()
    @get "/privacy", (context) -> privacy.display()

    # Polyfill Detection
    home.polyfill = ->
      if !Modernizr.input.placeholder
        $("input, textarea").placeholder()
    )

  # Run the application
  $ -> circle.run window.location.pathname

) jQuery
