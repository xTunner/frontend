# Placeholder Polyfill
# https://github.com/mathiasbynens/jquery-placeholder
$(window).load ->
  $("input, textarea").placeholder()

# Sammy
(($) ->
  circle = $.sammy("body", ->

    # Navigation
    @get "/", (context) -> loader "home"
    @get "#/about", (context) -> loader "about"

    # Render helper
    render = (page) ->
      $("body").attr("id",page).html HAML[page](renderContext)

    # Load helper
    loader = (page) ->
      if @HAML? and @HAML[page]?
        render page
      else
        $.getScript "assets/views/outer/#{page}/#{page}.hamlc", -> render page
  )

  # Run the application
  $ ->
    circle.run "#/"
) jQuery
