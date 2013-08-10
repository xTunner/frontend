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

# Prefer track_link for links to external sites, like github, because the redirect prevents JS from running/completing
ko.bindingHandlers.track_link =
  init: (el, valueAccessor) =>
    $(el).click (event) ->
      event.preventDefault()
      redirect = () ->
        window.location.replace($(el).attr('href'))
      backup_redirect = setTimeout(redirect, 1000)
      mixpanel.track valueAccessor(), {}, ->
        clearTimeout(backup_redirect)
        redirect()

# Takes a css selector, finds all child elements matching that selector and
# sets the width of all of the elements to the width of the max element
# Example:
# %div{data-bind: "foreach: items, equalizeWidth: '.item'"}
#   .item{data-bind: "text: $data"}
# Approach here: https://github.com/knockout/knockout/wiki/Bindings%3A-runafter
ko.bindingHandlers.equalizeWidth =
  update: (el, valueAccessor) =>
    setTimeout () ->
      selector = valueAccessor()
      els = $(el).find(selector)
      max = Math.max.apply(null, $.map(els, (e) -> $(e).width()))
      $.each(els, (i, e) -> $(e).width(max))

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
transformContent = (f, element, textContent) ->
  value = ko.utils.unwrapObservable(textContent)

  if not value?
    value = ""
  else
    value = f(value)

  if 'innerText' in element
    element.innerText = value
  else
    element.textContent = value

  if (ieVersion >= 9)
    element.style.display = element.style.display

ko.bindingHandlers.money =
  update: (el, valueAccessor) =>
    f = (value) -> "$#{addCommas(value)}"
    transformContent(f, el, valueAccessor())

ko.bindingHandlers.duration =
  update: (el, valueAccessor) =>
    f = (value) -> CI.time.as_duration(value)
    transformContent(f, el, valueAccessor())

ko.bindingHandlers.leadingZero =
  update: (el, valueAccessor) =>
    f = (value) -> "0#{value}"
    transformContent(f, el, valueAccessor())

ko.observableArray["fn"].setIndex = (index, newItem) ->
  @valueWillMutate()
  result = @()[index] = newItem
  @valueHasMutated()
  result
