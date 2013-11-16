# Hueristics for determining if a visitor to the website is a user
CI.ExistingUserHueristic = class ExistingUserHueristic
  constructor: () ->
    @is_existing_user = ko.observable()

    if window.renderContext.current_user
      @is_existing_user(true)
    else
      try
        if mixpanel.get_property?
          @set_from_mixpanel_cookie()
        else # wait for mixpanel script to load
          $(window).load () =>
            @set_from_mixpanel_cookie()
      catch e
        console.error e.message
        _rollbar.push e

    @is_existing_user.subscribe (answer) =>
      mixpanel.register
        existing_user: answer

  set_from_mixpanel_cookie: () =>
    @is_existing_user(!!mixpanel.get_property('mp_name_tag'))