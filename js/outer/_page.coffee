CI.outer.Page = class Page
  constructor: (@name, @_title, @mixpanelID=null) ->

  display: (cx) =>
    @setPageTitle(cx)

    @maybeTrackMixpanel()

    # Render content
    @render(cx)

    # Land at the right anchor on the page
    @scroll window.location.hash

    # Fetch page-specific libraries
    @placeholder()
    @follow()
    @lib() if @lib?

    ko.applyBindings(VM)

  maybeTrackMixpanel: () =>
    if @mixpanelID?
      mixpanel.track @mixpanelID

  viewContext: (cx) =>
    {}

  render: (cx) =>
    template = @name
    klass = "outer"

    args = $.extend renderContext, @viewContext(cx)
    header =
      $("<header></header>")
        .append(HAML.header(args))

    content =
      $("<main></main>")
        .attr("id", "#{@name}-page")
        .append(HAML[template](args))

    footer =
      $("<footer></footer>")
        .append(HAML["footer"](args))


    $('#app')
      .html("")
      .removeClass('outer')
      .removeClass('inner')
      .addClass(klass)
      .append(header)
      .append(content)
      .append(footer)

    if VM.ab().old_font()
      $('#app').addClass('old-font')



  scroll: (hash) =>
    if hash == '' or hash == '#' then hash = "body"
    if $(hash).offset()
      $('html, body').animate({scrollTop: $(hash).offset().top}, 0)

  title: =>
    @_title

  setPageTitle: (cx) =>
    document.title = @title(cx) + " - CircleCI"

  placeholder: () =>
    $("input, textarea").placeholder()

  follow: =>
    $("#twitter-follow-template-div").empty()
    clone = $(".twitter-follow-template").clone()
    clone.removeAttr "style" # unhide the clone
    clone.attr "data-show-count", "false"
    clone.attr "class", "twitter-follow-button"
    $("#twitter-follow-template-div").append clone

    # reload twitter scripts to force them to run, converting a to iframe
    $.getScript "//platform.twitter.com/widgets.js"
