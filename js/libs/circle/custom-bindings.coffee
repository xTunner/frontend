ko.bindingHandlers.popover =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    options = valueAccessor()
    $(el).popover(options)

# Takes any kind of jQueryExtension, e.g. popover, tooltip, etc.
# Example usage:
#   %a{href: "#", data-bind: "jQueryExtension: {type: 'tooltip', options: {title: myObservable}}"}
ko.bindingHandlers.jQueryExt =
  init: (el, valueAccessor) =>
    options = valueAccessor().options
    type = valueAccessor().type
    $el = $(el)
    $el[type].call($el, options)
