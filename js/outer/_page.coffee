CI.outer.Page = class Page
  constructor: (@name, @_title, @mixpanelID=null) ->

  display: (cx) =>
    @setPageTitle(cx)

    @maybeTrackMixpanel()

    # Render content
    @clearIntercom()
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
    if VM.ab().new_outer_old_copy()
      params = $.extend renderContext, @viewContext(cx)
      $('html').addClass('outer').removeClass('inner')
      $("#main").html HAML['header'](params)
      $("#main").append HAML[@name](params)
      $("#main").append HAML['footer_nav'](params)
    else
      params = $.extend renderContext, @viewContext(cx)
      $('html').addClass('old-outer').removeClass('inner')
      $('body').attr("id", "#{@name}-page")
      $("#main").html HAML['old_header'](params)
      $("#main").append HAML["old_#{@name}"](params)
      if @useStickyFooter? and @useStickyFooter
        $('#main > div').wrapAll "<div id='wrap' />"
      $("#main").append HAML['old_footer'](params)


  scroll: (hash) =>
    if hash == '' or hash == '#' then hash = "body"
    if $(hash).offset()
      $('html, body').animate({scrollTop: $(hash).offset().top}, 0)

  title: =>
    @_title

  setPageTitle: (cx) =>
    document.title = @title(cx) + " - CircleCI"

  clearIntercom: =>
    $('#IntercomTab').text "" # clear the intercom tab

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
