ko.bindingHandlers.popover =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    options = valueAccessor()
    $(el).popover(options)

ko.bindingHandlers.slider =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    options = valueAccessor()
    $(el).slider(options)

# Takes any kind of jQueryExtension, e.g. popover, tooltip, etc.
# Example usage:
#   %a{href: "#", data-bind: "jQueryExtension: {type: 'tooltip', options: {title: myObservable}}"}
ko.bindingHandlers.jQueryExt =
  init: (el, valueAccessor) =>
    options = valueAccessor().options
    type = valueAccessor().type
    $el = $(el)
    $el[type].call($el, options)

# Add helper that was minified
ieVersion = () ->
  version = 3
  div = document.createElement('div')
  iElems = div.getElementsByTagName('i')
  null while (div.innerHTML = "<!--[if gt IE #{++version}]><i></i><![endif]-->" and iElems[0])

  if version > 4 then version else undefined

addCommas = (num) ->
  num_str = num.toString()
  i = num_str.length % 3
  prefix = num_str.substr(0, i) + if i > 0 and num_str.length > 3 then "," else ""
  suffix = num_str.substr(i).replace(/(\d{3})(?=\d)/g, "$1" + ",")
  prefix + suffix


setMoneyContent = (element, textContent) ->
  value = ko.utils.unwrapObservable(textContent)
  if not value?
    value = ""
  else
    value = "$#{addCommas(value)}"

  if 'innerText' in element
    element.innerText = value
  else
    element.textContent = value

  if (ieVersion >= 9)
    element.style.display = element.style.display;

ko.bindingHandlers.money =
  update: (el, valueAccessor) =>
    setMoneyContent(el, valueAccessor())
