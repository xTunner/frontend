 ## A Repo comes from github, may or may not be in the DB yet
CI.inner.Repo = class Repo extends CI.inner.Obj
  observables: =>
    following: false

  constructor: (json) ->
    super json
    CI.inner.VcsUrlMixin(@)

    @canFollow = @komp =>
      not @following() and (@admin or @has_followers)

    @requiresInvite = @komp =>
      not @following() and not @admin and not @has_followers

    @displayName = @komp =>
      if @fork
        @project_name()
      else
        @name



  unfollow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/unfollow"
      success: (data) =>
        @following(false)
        _gaq.push(['_trackEvent', 'Repos', 'Remove']);

  follow: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/project/#{@project_name()}/follow"
      success: (data) =>
        _gaq.push(['_trackEvent', 'Repos', 'Add']);
        @following(true)
        if data.first_build
          (new CI.inner.Build(data.first_build)).visit()
        else
          $('html, body').animate({ scrollTop: 0 }, 0);
          VM.loadRecentBuilds()
