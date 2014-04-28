CI.inner.DashboardPage = class DashboardPage extends CI.inner.Page
  constructor: () ->
    super()
    @title = "Dashboard"
    @show_branch = true

  refresh: () ->
    page = BuildPager.currentPage()
    if page == 0
      VM.loadRecentBuilds(page, true)
