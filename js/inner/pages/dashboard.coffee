CI.inner.DashboardPage = class DashboardPage extends CI.inner.Page
  constructor: () ->
    super()
    @title = "Dashboard"
    @show_branch = true

  refresh: () ->
    VM.loadRecentBuilds(true)
