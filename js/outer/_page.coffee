CI.outer.Page = class Page
  constructor: (@name, @_title) ->

  display: (cx) =>
    @setPageTitle(cx)

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

  viewContext: (cx) =>
    {}

  render: (cx) =>
    params = $.extend renderContext, @viewContext(cx)
    $('html').addClass('outer').removeClass('inner')
    $('body').attr("id", "#{@name}-page")
    $('body').attr("class", if VM.ab().new_landing_page then 'b' else 'a')
    $("#main").html HAML['header'](params)
    $("#main").append HAML[@name](params)
    if @useStickyFooter? and @useStickyFooter
      $('#main > div').wrapAll "<div id='wrap' />"
    $("#main").append HAML['footer'](params)

  scroll: (hash) =>
    if hash == '' or hash == '#' then hash = "body"
    if $(hash).offset()
      $('html, body').animate({scrollTop: $(hash).offset().top}, 0)

  title: =>
    @_title

  setPageTitle: (cx) =>
    document.title = "Circle - " + @title(cx)

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
