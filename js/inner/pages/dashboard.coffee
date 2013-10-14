CI.inner.DashboardPage = class DashboardPage extends CI.inner.Page
  constructor: () ->
    super()
    @title = "Dashboard"

  refresh: () ->
    VM.loadRecentBuilds(true)
