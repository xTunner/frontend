noop = () ->
  null

CI.inner.Favicon = class Favicon extends CI.inner.Obj
  constructor: (@current_page) ->
    @favicon_updator = @komp noop

    @current_page.subscribe (current_page) =>
      @favicon_updator.dispose()

      @set_color(current_page.favicon_color())

  set_color: (color) =>
    $("link[rel='icon']").attr('href', assetPath("/favicon-#{color}.png?v=27"))

  reset_favicon: () =>
    @set_color() # undefined resets it
