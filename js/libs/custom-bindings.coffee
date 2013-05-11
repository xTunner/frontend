ko.bindingHandlers.popover =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    options = valueAccessor()
    $(el).popover(options)

ko.bindingHandlers.slider =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    options = valueAccessor()
    $(el).slider(options)

ko.bindingHandlers.track =
  init: (el, valueAccessor) =>
    $(el).click ->
      mixpanel.track(valueAccessor())

ko.bindingHandlers.track_link =
  init: (el, valueAccessor) =>
    $(el).click (event) ->
      event.preventDefault();
      mixpanel.track(valueAccessor())
      # setTimeout("window.location.replace($(el).attr('href'))", 2000)


# Takes any kind of jQueryExtension, e.g. popover, tooltip, etc.
jQueryExt = (type) =>
  init: (el, valueAccessor) =>
    options = valueAccessor()
    $el = $(el)
    $el[type].call($el, options)

# Usage: %a{href: "#", tooltip: {title: myObservable}}
ko.bindingHandlers.popover = jQueryExt('popover')
ko.bindingHandlers.tooltip = jQueryExt('tooltip')
ko.bindingHandlers.typeahead = jQueryExt('typeahead')

## Money custom binding

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

# Copy of setTextContent in ko's utils
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
    element.style.display = element.style.display

ko.bindingHandlers.money =
  update: (el, valueAccessor) =>
    setMoneyContent(el, valueAccessor())

setLeadingZeroContent = (element, textContent) ->
  value = ko.utils.unwrapObservable(textContent)
  if not value?
    value = ""
  else if value >= 0 and value < 10
    value = "0#{value}"

  if 'innerText' in element
    element.innerText = value
  else
    element.textContent = value

  if (ieVersion >= 9)
    element.style.display = element.style.display

ko.bindingHandlers.leadingZero =
  update: (el, valueAccessor) =>
    setLeadingZeroContent(el, valueAccessor())



ko.observableArray["fn"].setIndex = (index, newItem) ->
  @valueWillMutate()
  result = @()[index] = newItem
  @valueHasMutated()
  result
