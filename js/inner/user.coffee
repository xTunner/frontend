CI.inner.User = class User extends CI.inner.Obj
  observables: =>
    organizations: []
    collaboratorAccounts: []
    loadingOrganizations: false
    loadingRepos: false
    loadingUser: false
    # the org we're currently viewing in add-projects
    activeOrganization: null
    # keyed on org/account name
    repos: []
    tokens: []
    tokenLabel: ""
    herokuApiKeyInput: ""
    heroku_api_key: ""
    user_key_fingerprint: ""
    email_provider: ""
    all_emails: []
    selected_email: ""
    basic_email_prefs: "smart"
    plan: null
    parallelism: 1

  constructor: (json) ->
    super json,
      admin: false
      login: ""

    @environment = window.renderContext.env

    @showEnvironment = @komp =>
      @admin || (@environment is "staging") || (@environment is "development")

    @environmentColor = @komp =>
      result = {}
      result["env-" + @environment] = true
      result

    @in_trial = @komp =>
      # not @paid and @days_left_in_trial >= 0
      false

    @trial_over = @komp =>
      #not @paid and @days_left_in_trial < 0
      # need to figure "paid" out before we really show this
      false

    @showLoading = @komp =>
      @loadingRepos() or @loadingOrganizations()

    @plan_id = @komp =>
      @plan()

    @collaborator = (login) =>
      @komp =>
        for collaborator in @collaboratorAccounts()
          return collaborator if collaborator.login is login

    @collaboratorsWithout = (login) =>
      @komp =>
        c for c in @collaboratorAccounts() when c.login isnt login

  create_token: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/user/create-token"
      data: JSON.stringify {label: @tokenLabel()}
      success: (result) =>
        @tokens result
        @tokenLabel("")
        true
    false

  create_user_key: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/user/ssh-key"
      data: JSON.stringify {label: @tokenLabel()}
      success: (result) =>
        @user_key_fingerprint(result.user_key_fingerprint)
        true
    false

  delete_user_key: (data, event) =>
    $.ajax
      type: "DELETE"
      event: event
      url: "/api/v1/user/ssh-key"
      data: JSON.stringify {label: @tokenLabel()}
      success: (result) =>
        @user_key_fingerprint(result.user_key_fingerprint)
        true
    false

  save_heroku_key: (data, event) =>
    $.ajax
      type: "POST"
      event: event
      url: "/api/v1/user/heroku-key"
      data: JSON.stringify {apikey: @herokuApiKeyInput()}
      success: (result) =>
        true
        @heroku_api_key(@herokuApiKeyInput())
        @herokuApiKeyInput("")
    false

  save_preferences: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/user/save-preferences"
      data: JSON.stringify
        basic_email_prefs: @basic_email_prefs()
        selected_email: @selected_email()
      success: (result) =>
        @updateObservables(result)
    false

  save_basic_email_pref: (data, event) =>
    @basic_email_prefs(event.currentTarget.defaultValue)
    @save_preferences(data, event)
    true

  save_email_address: (data, event) =>
    @selected_email(event.currentTarget.defaultValue)
    @save_preferences(data, event)
    true

  loadOrganizations: () =>
    @loadingOrganizations(true)
    $.getJSON '/api/v1/user/organizations', (data) =>
      @organizations(data)
      @setActiveOrganization(data[0])
      @loadingOrganizations(false)


  loadCollaboratorAccounts: () =>
    @loadingOrganizations(true)
    $.getJSON '/api/v1/user/collaborator-accounts', (data) =>
      @collaboratorAccounts(data)
      @loadingOrganizations(false)

  setActiveOrganization: (org, event) =>
    if org
      @activeOrganization(org.login)
      @loadRepos(org)

  loadRepos: (org) =>
    @loadingRepos(true)
    if org.org
      url = "/api/v1/user/org/#{org.login}/repos"
    else
      url = "/api/v1/user/user/#{org.login}/repos"

    $.getJSON url, (data) =>
      @repos((new CI.inner.Repo r for r in data))
      @loadingRepos(false)

  syncGithub: () =>
    @loadingUser(true)
    $.getJSON '/api/v1/sync-github', (data) =>
      @updateObservables(data)
      @loadingUser(false)

  isPaying: () =>
    @plan?
