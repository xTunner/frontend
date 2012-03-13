@Circle = {} unless @Circle
@Circle.time =
  yesterday: () ->
    moment(Date.now()).subtract("days", 1).sod()

  ## takes two Dates, returns a human friendly string about how long it took
  pretty_duration_short: (duration) ->
    if not duration
      return ""

    before = moment(0)
    after = moment(duration)

    days = after.diff before, "days"
    hours = after.diff before, "hours"
    minutes = after.diff before, "minutes"
    seconds = after.diff before, "seconds"

    if seconds < 45
      "#{seconds}s"
    else if minutes < 60
      "#{minutes}m"
    else if hours < 24
      "#{hours}h:#{minutes}m"
    else if hours < 24
      after.format "h:mma"
    else if days < 6
      after.format "dddd \\at h:mma"
    else if days < 365
      after.format "MMM D \\at h:mma"
    else
      after.format "MMM D, YYYY"

  update_timestamps: () ->
    $('.utc_time').each ->
      # We receive the number of milliseconds since the epoch, in UTC. JS's Date
      # functions assume UTC, so we actually don't need to do anything for the
      # conversion.
      utc = $(this).text()
      utc = moment(parseInt utc)
      $(this).text pretty_duration(utc)
      $(this).attr "title", utc.toDate()