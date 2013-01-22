CI.stringHelpers =
  trimMiddle: (str, max_len) =>
    str_len = str.length
    if str_len <= max_len + 3
      str
    else
      over = str_len - max_len
      start_slice = Math.ceil(max_len / 3)
      str.slice(0, start_slice) + "..." + str.slice(start_slice + over)
