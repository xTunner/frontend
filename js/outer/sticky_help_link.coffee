CI.outer.StickyHelpLink =
  stickyHelpLinkOpen: ko.observable(false)

  toggleStickyHelpLinkOpen: () =>
    CI.outer.StickyHelpLink.stickyHelpLinkOpen(!CI.outer.StickyHelpLink.stickyHelpLinkOpen())
