CI.stringHelpers =
  trimMiddle: (str, goal_len) =>
    str_len = str.length
    if str_len <= goal_len + 3
      str
    else
      over = str_len - goal_len + 3
      start_slice = Math.ceil((goal_len - 3) / 3)
      str.slice(0, start_slice) + "..." + str.slice(start_slice + over)

  linkify: (text, project_name) ->
    # urlPattern and psudoUrlPattern are taken from http://stackoverflow.com/a/7123542

    # http://, https://, ftp://
    urlPattern = /(\b(https?|ftp):\/\/[-A-Za-z0-9+&@#\/%?=~_|!:,.;]*[-A-Za-z0-9+&@#\/%=~_|])/g

    # www. sans http:// or https://
    pseudoUrlPattern = /(^|[^\/])(www\.[\S]+(\b|$))/g

    text = text.replace(urlPattern, '<a href="$1" target="_blank">$1</a>')
               .replace(pseudoUrlPattern, '$1<a href="http://$2" target="_blank">$2</a>')

    if project_name
      [org, repo] = project_name.split "/"

      # org/branch
      # Note: this could be a different org or user, but we would probably get too many false
      # positives if we linkify every word/pair. GitHub can do this because it's easy for
      # them to tell which org/branch pairs are real, but this is too expensive for us.
      # So, let's only linkify org/branch paths for the project's org.
      branchPattern = new RegExp("(#{org}\\/(\\w+))", "g")
      branchUrl = "https://github.com/#{project_name}/tree/$2"

      # #issueNum
      issuePattern = /\#(\d*)/g
      issueUrl = "https://github.com/#{project_name}/issues/$1"

      text = text.replace(branchPattern, "<a href='#{branchUrl}' target='_blank'>$1</a>")
                 .replace(issuePattern, "<a href='#{issueUrl}' target='_blank'>#$1</a>")

    text
