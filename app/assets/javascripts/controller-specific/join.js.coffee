join_path = "/join"
dynamic_path = "/join/dynamic"
poller = () ->
  $.getJSON(dynamic_path,
    (data, textStatus) ->
      if data["ready"]
        adjust_page(data["step"], data["body"], data["explanation"])
      if data["keep_polling"]
        setTimeout(poller, 200)
  )

adjust_page = (step, body, explanation) ->
  for i in [1,2,3]
    if i == step
      if body
        node = $("#body#{i} > .contents")
        node.empty()
        node.append(body)
      if explanation
        node = $("#explanation#{i} > .contents")
        node.empty()
        node.append(explanation)
      $("#header#{i}").removeClass("info")
      $("#header#{i}").addClass("success")
      $("#body#{i}").removeClass("hidden")
      $("#explanation#{i}").removeClass("hidden")
    else
      $("#header#{i}").removeClass("success")
      $("#header#{i}").addClass("info")
      $("#body#{i}").addClass("hidden")
      $("#explanation#{i}").addClass("hidden")

$(document).ready ->
  setTimeout(poller, 200)
  history.replaceState(null, null, join_path)
