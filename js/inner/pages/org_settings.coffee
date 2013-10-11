CI.inner.OrgSettingsPage = class OrgSettingsPage extends CI.inner.Page
  constructor: (properties) ->
    @org_name = null
    super(properties)
    @crumbs = [] # ['org', 'org_settings']

    @title = "Org settings - #{@org_name}"
