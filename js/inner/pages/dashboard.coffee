CI.inner.DashboardPage = class DashboardPage extends CI.inner.Page
  constructor: (properties) ->
    super(properties)
    @title = "Dashboard"
    @show_branch = true

    @older_page_button_style = ko.computed =>
      if VM.builds().length < VM.builds_per_page
        disabled: true

    @newer_page_button_style = ko.computed =>
      location = SammyApp.getLocation()

      uri = URI(location)
      query_params = uri.search(true)
      page = parseInt(query_params?.page) or 0

      # Just accessing VM.builds() to force re-evaluation of this computed
      # observable when the build list changes
      if VM.builds() && page <= 0
        disabled: true

  current_page: () ->
    uri = URI(SammyApp.getLocation())
    query_params = uri.search(true)
    page = parseInt(query_params?.page) or 0

  change_builds_page: (update_page_fn) =>
    location = SammyApp.getLocation()

    uri = URI(location)
    query_params = uri.search(true)
    page = parseInt(query_params?.page) or 0

    new_page = if page? then update_page_fn(page) else 0

    # Avoid a page reload going from circleci.com to circleci.com/?page=0
    if new_page != page
      query_params.page = new_page
      uri.search(query_params)
      SammyApp.setLocation(uri.toString())

  load_older_page: (object) =>
    @change_builds_page((page_num) =>
      if VM.builds().length >= VM.builds_per_page
        page_num + 1
      else
        page_num)

  load_newer_page: (object) =>
    @change_builds_page((page_num) =>
      if page_num > 0
        page_num - 1
      else
        0)

  refresh: () ->
    page = @current_page()
    if page is 0
      VM.loadRecentBuilds(page, true)
