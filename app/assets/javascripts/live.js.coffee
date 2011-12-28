pusher = () ->
  key = '3a8c215e8630b2155d5c'
  pusher = new Pusher key
  channel = pusher.subscribe 'project_channel'
  channel.bind 'my_event', (data) ->
    alert data
