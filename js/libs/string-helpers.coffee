CI.stringHelpers =
  trimMiddle: (str, goal_len) =>
    str_len = str.length
    if str_len <= goal_len + 3
      str
    else
      over = str_len - goal_len + 3
      start_slice = Math.ceil((goal_len - 3) / 3)
      str.slice(0, start_slice) + "..." + str.slice(start_slice + over)

  # CoffeeScript translation of http://stackoverflow.com/a/7123542
  linkify: (inputText) ->
    # http://, https://, ftp://
    urlPattern = /(\b(https?|ftp):\/\/[-A-Z0-9+&@#\/%?=~_|!:,.;]*[-A-Z0-9+&@#\/%=~_|])/gim

    # www. sans http:// or https://
    pseudoUrlPattern = /(^|[^\/])(www\.[\S]+(\b|$))/gim

    inputText.replace(urlPattern, '<a href="$1" target="_blank">$1</a>')
             .replace(pseudoUrlPattern, '$1<a href="http://$2" target="_blank">$2</a>')