CI.paths =
  org_settings: (org_name, subpage) =>
    path = "/gh/organizations/#{org_name}/settings"
    path += "##{subpage.replace('_', '-')}" if subpage
    path
