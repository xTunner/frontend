CI.Timer = class Timer
  constructor: (start_at) ->
    # eventually start will be options, where we start from minutes or something
    @start_at = start_at
    @current_timeout = null
    @stop_updating = false
    @started = false
    @timer = ko.observable()

  start: () =>
    @started = true

    # use timestamps b/c isBefore and isAfter are slow
    @minute_transition = moment(@start_at).add('minutes', 60).unix()
    @hour_transition = moment(@start_at).add('hours', 24).unix()
    @day_transition = moment(@start_at).add('days', 2).unix()

    @set_next_update()
    @update_timer()
    @timer()

  maybe_start: () =>
    if not @started
      @start()
    else
      @update_timer()

    @timer()

  stop: () =>
    clearTimeout(@current_timeout)
    @started = false
    @stop_updating = true

  update_timer: () =>
    console.log "updating"
    @timer(moment().diff(@start_at))

  has_subscribers: () =>
    @timer.getSubscriptionsCount() > 0

  set_next_update: () =>
    now = moment().unix()
    if now < @minute_transition
      timeout = 1000
    else if now < @hour_transition
      timeout = 60 * 1000
    else if now < @day_transition
      timeout = 60 * 60 * 1000
    else
      timeout = 24 * 60 * 60 * 1000

    console.log "timeout is", timeout
    @current_timeout = window.setTimeout () =>
      clearTimeout(@current_timeout) # just in case

      # it's possible that there is a subscriber race
      # here if setup takes longer than 1 sec
      if not @stop_updating and @has_subscribers()
        @update_timer()
        @set_next_update()
    , timeout
