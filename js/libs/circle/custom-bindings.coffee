ko.bindingHandlers.popover =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    options = valueAccessor()
    $(el).popover(options)

ko.bindingHandlers.slider =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    options = valueAccessor()
    $(el).slider(options)

# returns random integer in range [0, n)
randInt = (n) ->
  Math.floor(Math.random() * n)

# The a/b tests should be an observable on viewModel called ab_tests()
# If an ab_test hasn't been chosen, it will set a random value and send it to
# Kissmetrics
# Example usage:
#   %p{href: "#", data-bind: "ab_test: {test: 'Show beta text', option: true}"}
#     This is the text that will show up if option is set to true by KissMetrics!
#   %p{href: "#", data-bind: "ab_test: {test: 'Show beta text', option: false}"}
#     This is the text that will show up if option is set to false by KissMetrics!
ko.bindingHandlers.ab_test =
  init: (el, valueAccessor, allBindingsAccessor, viewModel, bindingContext) =>
    test = valueAccessor().test
    option = valueAccessor().option

    if viewModel.ab_tests?
      ab_tests = viewModel.ab_tests()
    else
      console.log 'ab_tests is not defined on the viewModel!'
      ab_tests = {}


    # set default option to first one if the a/b test isn't defined in
    # ab-test-definitions.js
    if not ab_tests[test]?
      console.log "No A/B test definitions for '#{test}'!"

      ab_tests[test] =
        options: [option]
        chosen_p: true
        choice: option
      viewModel.ab_tests(ab_tests)

      console.log "Chose #{option} for test '#{test}', anyway"

    if not ab_tests[test].chosen_p?
      console.log "No option is chosen for '#{test}', we'll try again in a bit"
      circle_log_time()
      window.setTimeout () ->
        ab_tests = viewModel.ab_tests()
        if not ab_tests[test].chosen_p?
          i = randInt(ab_tests[test].options.length)
          choice = ab_tests[test].options[i]
          ab_tests[test].chosen_p = true
          ab_tests[test].choice = choice

          viewModel.ab_tests(ab_tests)

          console.log "Too late for test '#{test}' from KM, selected #{choice}"
          circle_log_time()

          # send the choice manually to KissMetrics
          # I don't think this actually works, waiting for KM to email me (dwwoelfel)
          kmq_choice = {}
          kmq_choice[test] = choice
          _kmq.push(["set", kmq_choice])

        display_p = ab_tests[test].choice is option

        console.log("Displaying #{option} for test '#{test}':", display_p)

        if display_p then $(el).css('display', '')

      , 100


    display_p = ab_tests[test].choice is option

    console.log("Displaying #{option} for test '#{test}':", display_p)
    circle_log_time()

    if not display_p then $(el).css('display', 'none')

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
    element.style.display = element.style.display

ko.bindingHandlers.money =
  update: (el, valueAccessor) =>
    setMoneyContent(el, valueAccessor())
