noop = () ->
  null

CI.inner.Favicon = class Favicon extends CI.inner.Obj
  constructor: (@selected) ->
    @favicon_updator = @komp noop

    @selected.subscribe (selected) =>
      @favicon_updator.dispose()

      if selected.favicon_updator?
        @favicon_updator = @komp selected.favicon_updator
      else
        @reset_favicon()

  set_color: (color) =>
    $("link[rel='icon']").attr('href', assetPath("/favicon-#{color}.png?v=27"))

  build_updator: (build) =>
    if build?
      @set_color(build.favicon_color())

  reset_favicon: () =>
    @set_color() # undefined resets it
