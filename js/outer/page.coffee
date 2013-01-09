CI.outer.Page = class Page
  constructor: (@name, @title) ->

  display: (cx) =>
    document.title = "Circle - " + @title

    # Render content
    @render(cx)

    # Land at the right anchor on the page
    @scroll window.location.hash

    # Fetch page-specific libraries
    @placeholder()
    @follow()
    @lib() if @lib?

    ko.applyBindings(VM)

  render: (cx) =>
    $("body").attr("id","#{@name}-page").html HAML['header'](renderContext)
    $("body").append HAML[@name](renderContext)
    $("body").append HAML['footer'](renderContext)

  scroll: (hash) =>
    if hash == '' or hash == '#' then hash = "body"
    $('html, body').animate({scrollTop: $(hash).offset().top}, 0)

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
