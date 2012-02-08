pretty_wording = (date) ->
  now = moment(Date.now())
  relative = now.diff date, "days"

  if relative < 3
    date.fromNow()
  else
    date.calendar()


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
