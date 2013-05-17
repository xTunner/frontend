# Setup AJAX calls according to our preferences


textVal = (elem, val) ->
  "Takes a jquery element and gets or sets either val() or text(), depending on what's appropriate for this element type (ie input vs button vs a, etc)"
  if elem.is("input")
    if val? then elem.val val else elem.val()
  else # button or a
    if val? then elem.text(val) else elem.text()

finishAjax = (event, attrName, buttonName) ->
  if event
    t = $(event.currentTarget)
    done = t.attr(attrName) or buttonName
    if not (t.prop("type").toLowerCase() in ["radio", "checkbox"])
      textVal t, done

    func = () =>
      textVal t, event.savedText
      t.removeClass "disabled"
    setTimeout(func, 1500)

$(document).ajaxSuccess (ev, xhr, options) ->
  finishAjax(xhr.event, "data-success-text", "Saved")

$(document).ajaxError (ev, xhr, settings, errorThrown) ->
  finishAjax(xhr.event, "data-failed-text", "Failed")
  resp = xhr.responseText
  if xhr.status == 401
    notifyError "You've been logged out, log back in to continue."
  else if resp.indexOf("<!DOCTYPE") is 0 or resp.length > 500
    notifyError "An unknown error occurred: (#{xhr.status} - #{xhr.statusText})."
  else
    notifyError (xhr.responseText or xhr.statusText)

$(document).ajaxSend (ev, xhr, options) ->
  xhr.event = options.event
  if xhr.event
    t = $(xhr.event.currentTarget)
    t.addClass "disabled"
    # change to loading text
    loading = t.attr("data-loading-text") or "..."
    xhr.event.savedText = textVal t
    if not (t.prop("type").toLowerCase() in ["radio", "checkbox"])
      textVal t, loading


# Make the buttons disabled when clicked
CI.ajax =
  init: () =>
    $.ajaxSetup
      contentType: "application/json"
      accepts: {json: "application/json"}
      dataType: "json"
