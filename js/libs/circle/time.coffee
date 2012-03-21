@Circle = {} unless @Circle
@Circle.time =
  yesterday: () =>
    moment(Date.now()).subtract("days", 1).sod()

  ## takes two Dates, returns a human friendly string about how long it took
  as_time_since: (time_string) =>
    date = moment(time_string)
    now = moment(Date.now())
    yesterday = @Circle.time.yesterday()

    days = now.diff date, "days"
    hours = now.diff date, "hours"
    minutes = now.diff date, "minutes"
    seconds = now.diff date, "seconds"

    if seconds < 45
      "#{seconds} seconds ago"
    else if date.clone().sod().diff(yesterday) == 0
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


  as_duration: (duration) =>
    if not duration
      return ""

    seconds = Math.floor(duration / 1000)
    minutes = Math.floor(seconds / 60)
    hours = Math.floor(minutes / 60)

    if hours > 8
      "#{hours}h"
    else if hours > 0
      "#{hours}h #{minutes % 60}m"
    else if minutes > 8
      "#{minutes}m"
    else if minutes > 0
      "#{minutes}m #{seconds % 60}s"
    else
      "#{seconds}s"
