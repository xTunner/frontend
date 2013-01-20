CI.outer.Pricing = class Pricing extends CI.outer.Page
  popover: () =>
    $('html').popover
      html: true
      placement: "bottom"
      template: '<div class="popover billing-popover"><div class="popover-inner"><h3 class="popover-title"></h3><div class="popover-content"><p></p></div></div></div>'
      delay: 0
      trigger: "hover"
      selector: ".more-info"
