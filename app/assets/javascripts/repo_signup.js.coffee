

poller = (retry) ->
  f = $("#status").data("fetcher")
  $.getJSON("/hooks/fetcher/" + f,
            (data, textStatus) ->
                if textStatus == 404
                    retry()
                else if data
                    alert("success")
                    # TODO: fix redirections
                else
                    alert("backward")
                    history.back()
  )




$(document).ready ->
  $.smartPoller(10, poller)
