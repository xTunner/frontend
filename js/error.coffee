$ ->
  # Set the title
  document.title = "Circle - " + renderContext.title

  # Display page
  $("body").attr("id","error").html HAML['header'](renderContext)
  $("body").append HAML['error'](renderContext)
  $('body > div').wrapAll('<div id="wrap"/>');
  $("body").append HAML['footer'](renderContext)
