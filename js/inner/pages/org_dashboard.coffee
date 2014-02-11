CI.inner.OrgDashboardPage = class OrgDashboardPage extends CI.inner.Page
  constructor: (properties) ->
    @username = null
    super(properties)
    @crumbs = [new CI.inner.OrgCrumb(@username, {active: true}),
               new CI.inner.OrgSettingsCrumb(@username)]

    @title = @username
    @show_branch = true
