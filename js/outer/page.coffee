CI.outer.Page = class Page
  constructor: (@name, @title) ->

  display: (cx) =>
    document.title = "Circle - " + @title

    # Render content
    @render(cx)

    # Land at the right anchor on the page
    @scroll window.location.hash

    # Fetch page-specific libraries
    @lib() if @lib?

    ko.applyBindings(OuterVM)

  render: (cx) =>
    $("body").attr("id","#{@name}-page").html HAML['header'](renderContext)
    $("body").append HAML[@name](renderContext)
    $("body").append HAML['footer'](renderContext)

  scroll: (hash) =>
    if hash == '' or hash == '#' then hash = "body"
    $('html, body').animate({scrollTop: $(hash).offset().top}, 0)
