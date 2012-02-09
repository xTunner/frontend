@yesterday = () ->
  moment(Date.now()).subtract("days", 1).sod()

@pretty_wording = (date) ->
  date = moment(date)
  now = moment(Date.now())

  days = now.diff date, "days"
  hours = now.diff date, "hours"
  minutes = now.diff date, "minutes"
  seconds = now.diff date, "seconds"

  if seconds < 45
    "#{seconds} seconds ago"
  else if date.clone().sod().diff(yesterday()) == 0
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


@update_timestamps = () ->
  $('.utc_time').each ->
    # We receive the number of milliseconds since the epoch, in UTC. JS's Date
    # functions assume UTC, so we actually don't need to do anything for the
    # conversion.
    utc = $(this).text()
    utc = moment(parseInt utc)
    $(this).text pretty_wording(utc)
    $(this).attr "title", utc.toDate()
