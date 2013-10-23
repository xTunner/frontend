noop = () ->
  null

CI.inner.Favicon = class Favicon extends CI.inner.Obj
  constructor: (@current_page) ->
    @current_page.subscribe (current_page) =>
      current_page.favicon_color.subscribe (favicon_color) =>
        @set_color(current_page.favicon_color())

  get_color: =>
    $("link[rel='icon']").attr('href').match(/favicon-([^.]+).png/)[1]

  set_color: (color) =>
    $("link[rel='icon']").attr('href', assetPath("/favicon-#{color}.png?v=27"))

  reset_favicon: () =>
    @set_color() # undefined resets it
