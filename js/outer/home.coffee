class CI.outer.Home extends CI.outer.Page
  lib: () =>
    _kmq.push(['record', "showed join link"])
    _kmq.push(['trackClickOnOutboundLink', '#join', 'hero join link clicked'])
    _kmq.push(['trackClickOnOutboundLink', '.kissAuthGithub', 'join link clicked'])
    _kmq.push(['trackClickOnOutboundLink', '#second-join', 'footer join link clicked'])
    _kmq.push(['trackSubmit', '#beta', 'beta form submitted'])
    _gaq.push(['_trackPageview', '/homepage'])
