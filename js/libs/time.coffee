CI.time =
  yesterday: () =>
    moment(Date.now()).subtract("days", 1).startOf('day')

  ## takes two Dates, returns a human friendly string about how long it took
  as_time_since: (time_string) =>
    date = moment(time_string)
    now = moment(Date.now())
    yesterday = CI.time.yesterday()

    days = now.diff date, "days"
    hours = now.diff date, "hours"
    minutes = now.diff date, "minutes"
    seconds = now.diff date, "seconds"

    if seconds is 1
      "1 second ago"
    else if seconds < 45
      "#{seconds} seconds ago"
    else if date.clone().startOf('day').diff(yesterday) == 0
      "Yesterday at " + date.format "h:mma"
    else if minutes < 30
      date.from now # use instead of fromNow because fromNow isn't properly mocked by sinon
    else if hours < 5
      date.from(now) + date.format " (h:mma)"
    else if hours < 24
      date.format "h:mma"
    else if days < 6
      date.format "dddd \\at h:mma"
    else if days < 365
      date.format "MMM D \\at h:mma"
    else
      date.format "MMM D, YYYY"

  as_since_condensed: (time_string) =>
    date = moment(time_string)
    now = moment(Date.now())
    yesterday = CI.time.yesterday()

    days = now.diff date, "days"
    hours = now.diff date, "hours"
    minutes = now.diff date, "minutes"
    seconds = now.diff date, "seconds"

    if minutes < 1
      "just now"
    else if hours < 1
      "#{minutes}m ago"
    else if date.clone().startOf('day').diff(yesterday) is 0 and hours > 18 and hours < 48
      "yesterday"
    else if days < 1
      date.format "h:mma"
    else if days < 365
      date.format "MMM D"
    else
      date.format "MMM YYYY"

  as_duration: (duration) =>
    if not duration
      return ""

    seconds = Math.floor(duration / 1000)
    minutes = Math.floor(seconds / 60)
    hours = Math.floor(minutes / 60)

    display_seconds = seconds % 60
    if display_seconds < 10
      display_seconds = "0" + display_seconds
    display_minutes = minutes % 60
    if display_minutes < 10
      display_minutes = "0" + display_minutes

    if hours > 0
      "#{hours}h #{display_minutes}m"
    else if minutes >= 1
      "#{minutes}m #{display_seconds}s"
    else
      "#{seconds}s"

  as_duration_condensed: (duration) =>
    if not duration
      return ""

    seconds = Math.floor(duration / 1000)
    minutes = Math.floor(seconds / 60)
    hours = Math.floor(minutes / 60)

    display_seconds = seconds % 60
    if display_seconds < 10
      display_seconds = "0" + display_seconds
    display_minutes = minutes % 60
    if display_minutes < 10
      display_minutes = "0" + display_minutes

    if hours > 0
      "#{hours}:#{display_minutes}:#{display_seconds}"
    else
      "#{display_minutes}:#{display_seconds}"

  as_estimated_duration: (duration) =>
    seconds = Math.floor(duration / 1000)
    minutes = Math.floor(seconds / 60)

    "#{minutes+1}m"
