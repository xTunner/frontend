# Ordered dependencies for Sammy
define [ "outer" ], () ->
  circle = $.sammy("body", ->

    # Page
    class Page
      constructor: (@name, @title) ->

      render: ->
        document.title = "Circle - " + @title
        $('html, body').animate({ scrollTop: 0 }, 0);
        $("body").attr("id","#{@name}-page").html HAML['header'](renderContext)
        $("body").append HAML[@name](renderContext)
        $("body").append HAML['footer'](renderContext)

        # Apply polyfill(s) if they exists for the page
        @polyfill() if @polyfill?

        # Sammy eats hashes, so we need to reapply it to land at the right anchor on the page
        hash = window.location.hash
        if hash != '' and hash != '#'
          window.location.hash = hash

      load: (show) ->
        self = this
        require [ "views/outer/#{@name}/#{@name}" ], () ->
          $ -> self.render()

      display: ->
        if HAML? and HAML[@name]? @render() else @load()

    # Pages
    home = new Page("home", "Continuous Integration made easy")
    about = new Page("about", "About Us")
    privacy = new Page("privacy", "Privacy Policy")

    # Navigation
    @get "/", (context) -> home.display()
    @get "/about.*", (context) -> about.display()
    @get "/privacy.*", (context) -> privacy.display()

    # Polyfill Detection
    home.polyfill = ->
      if !Modernizr.input.placeholder then require [ "placeholder" ]
    )

  # Run the application
  circle.run window.location.pathname
