pretty_wording = (date) ->
  now = moment(Date.now())
  days = now.diff date, "days"
  hours = now.diff date, "hours"
  minutes = now.diff date, "minutes"

  if minutes < 30
    date.fromNow()
  else if hours < 5
    date.fromNow() + date.format "(HH:mm)"
  else if hours < 24
    date.format "h:mm a"
  else if days < 6
    date.format "ddd \\at HH:mm"
  else if days < 365
    date.format "MMM Do (HH:mm)"
  else
    date.format "MMM Do, YY"


update_timestamps = () ->
  $('.utc_time').each ->
    # We receive the number of milliseconds since the epoch, in UTC. JS's Date
    # functions assume UTC, so we actually don't need to do anything for the
    # conversion.
    utc = $(this).text()
    utc = moment(parseInt(utc))
    $(this).text pretty_wording(utc)
    $(this).attr "title", utc.toDate()


$(document).ready ->
  update_timestamps()
