CI.Browser or= {}

CI.Browser.scroll_to = (position) =>
  element = if jQuery.browser.webkit then "body" else "html"
  offset = if position == "top" then 0 else document.body.offsetHeight
  $(element).animate({scrollTop: offset}, 0)
