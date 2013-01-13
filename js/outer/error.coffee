CI.outer.Error = class Error extends CI.outer.Page
  render: (cx) =>
    error = renderContext.status or 404
    url = renderContext.githubPrivateAuthURL
    titles =
      401: "Login required"
      404: "Page not found"
      500: "Internal server error"

    messages =
      401: "<a href=\"#{url}\">You must <b>log in</b> to view this page.</a>"
      404: "We're sorry, but that page doesn't exist."
      500: "We're sorry, but something broke."

    title = titles[error] or "Something unexpected happened"
    message = messages[error] or "Something completely unexpected happened"

    # Set the title
    document.title = "Circle - " + title

    # Display page
    $('#IntercomTab').text ""
    $("body").attr("id","error")
    $('#main').html HAML['header'](renderContext)
    $("#main").append HAML['error'](title: title, error: renderContext.status, message: message)
    $('#main > div').wrapAll "<div id='wrap' />" # this is the only page on which we want a sticky footer
    $("#main").append HAML['footer'](renderContext)
