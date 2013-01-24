CI.stringHelpers =
  trimMiddle: (str, goal_len) =>
    str_len = str.length
    if str_len <= goal_len + 3
      str
    else
      over = str_len - goal_len + 3
      start_slice = Math.ceil((goal_len - 3) / 3)
      str.slice(0, start_slice) + "..." + str.slice(start_slice + over)
