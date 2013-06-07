CI.Pusher = class Pusher
  constructor: (@login) ->
    key = switch window.renderContext.env
      when "production" then "6465e45f8c4a30a2a653"
      else "3f8cb51e8a23a178f974"

    @pusher = new window.Pusher(key, { encrypted: true})

    window.Pusher.channel_auth_endpoint = "/auth/pusher"

    @userSubscribePrivateChannel()
    @setupBindings()


  userSubscribePrivateChannel: () =>
    channel_name = "private-" + @login
    @user_channel = @pusher.subscribe(channel_name)
    @user_channel.bind 'pusher:subscription_error', (status) ->
      if _rollbar? && _rollbar.push?
        _rollbar.push status

  subscribe: (args...) =>
    @pusher.subscribe.apply @pusher, args

  setupBindings: () =>
    @user_channel.bind "call", (data) =>
      VM[data.fn].apply(@, data.args)
