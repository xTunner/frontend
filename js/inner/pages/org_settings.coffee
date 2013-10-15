CI.inner.OrgSettingsPage = class OrgSettingsPage extends CI.inner.Page
  constructor: (properties) ->
    @org_name = null
    super(properties)
    @crumbs = [new CI.inner.OrgCrumb(@username),
               new CI.inner.OrgSettingsCrumb(@username, {active: true})]

    @title = "Org settings - #{@org_name}"
