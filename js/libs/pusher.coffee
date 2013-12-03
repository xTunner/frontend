CI.Pusher = class Pusher
  constructor: (@login) ->
    key = switch window.renderContext.env
      when "production" then "961a1a02aa390c0a446d"
      else "3f8cb51e8a23a178f974"

    @pusher = new window.Pusher(key,
      encrypted: true
      auth:
        params:
          CSRFToken: CSRFToken)

    window.Pusher.channel_auth_endpoint = "/auth/pusher"

    @userSubscribePrivateChannel()
    @setupBindings()


  userSubscribePrivateChannel: () =>
    channel_name = "private-" + @login
    @user_channel = @pusher.subscribe(channel_name)
    @user_channel.bind 'pusher:subscription_error', (status) ->
      _rollbar.push status

  subscribe: (args...) =>
    @pusher.subscribe.apply @pusher, args

  unsubscribe: (channelName) =>
    @pusher.unsubscribe(channelName)

  setupBindings: () =>
    @user_channel.bind "call", (data) =>
      VM[data.fn].apply(@, data.args)
