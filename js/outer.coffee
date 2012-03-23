# Placeholder Polyfill
# https://github.com/mathiasbynens/jquery-placeholder
$(window).load ->
  $("input, textarea").placeholder()

# Sammy
(($) ->
  circle = $.sammy("body", ->

    # Page
    class Page
      constructor: (@name, @title) ->

      render: ->
        document.title = "Circle - " + @title
        $("body").attr("id",@name).html HAML[@name](renderContext)

      load: ->
        if HAML? and HAML[@name]?
          @render()
        else
          self = this
          $.getScript "assets/views/outer/#{@name}/#{@name}.hamlc", -> self.render()

    # Pages
    home = new Page("home", "Continuous Integration made easy")
    about = new Page("about", "About Us")

    # Navigation
    @get "/", (context) -> home.load()
    @get "#/about", (context) -> about.load()

  )

  # Run the application
  $ ->
    circle.run "#/"
) jQuery
