# Placeholder Polyfill
# https://github.com/mathiasbynens/jquery-placeholder
$(window).load ->
  $("input, textarea").placeholder()

# Sammy
(($) ->
  circle = $.sammy("#main", ->

    @element_selector = "body"

    # Home
    @get "/", (context) ->
      $.getScript "assets/views/outer/home/home.hamlc", ->
        $('body').html HAML['home'](renderContext)
        $('body').attr 'id': 'home'

    # About
    @get "#/about", (context) ->
      $.getScript "assets/views/outer/about/about.hamlc", ->
        $('body').html HAML['about'](renderContext)
        $('body').attr 'id': 'about'
  )

  # Run the application
  $ ->
    circle.run "#/"
) jQuery
