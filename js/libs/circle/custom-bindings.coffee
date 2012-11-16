ko.bindingHandlers.popover =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    options = valueAccessor()
    $(el).popover(options)

ko.bindingHandlers.slider =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    options = valueAccessor()
    $(el).slider(options)