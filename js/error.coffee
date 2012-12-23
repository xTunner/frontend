$ ->
  error = renderContext.error
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
  $("body").attr("id","error").html HAML['header'](renderContext)
  $("body").append HAML['error'](title: title, error: renderContext.error, message: message )
  $('body > div').wrapAll('<div id="wrap"/>');
  $("body").append HAML['footer'](renderContext)
