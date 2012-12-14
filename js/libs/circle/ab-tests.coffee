# Make sure _ab_test_definitions is defined. Either define it here,
# or define it window._ab_test_definitions. Format is 'test-name': [options...]
#
# Example:
# _ab_test_definitions =
#   'daniel-test': ['option1', 'option2']
#   'daniel-test-2': ['option1', 'option2', 'option3']
#
# Caveats:
#   Don't set null as an option.
#   You can't change the options, you need to define a new test
#
# Example usage in view:
#   %p{href: "#", data-bind: "ab_test: {test: 'Show beta text', option: true}"}
#     This is the text that will show up if option is set to true by KissMetrics!
#   %p{href: "#", data-bind: "ab_test: {test: 'Show beta text', option: false}"}
#     This is the text that will show up if option is set to false by KissMetrics!

## Add your A/B tests here
_ab_test_definitions =
  daniel_test_2: [true, false]

cookie_prefix = "ab_test_"
$.cookie.json = true
$.cookie.defaults.expires = 365
$.cookie.defaults.path = "/"

randInt = (n) ->
  Math.floor(Math.random() * n)

class ABTestViewModel
  constructor: () ->
    # defs don't need to be an observable, but we may want to do
    # inference in the future. Better to set it up now
    @test_definitions = ko.observable(_ab_test_definitions)

    # @ab_tests is an object with format {'test_name': "chosen_option", ...}
    @ab_tests = ko.observable({})

    @setup_tests()

  set_option: (test_name, value) =>
    tests = @ab_tests()
    tests[test_name] = value
    @ab_tests(tests)
    $.cookie(cookie_prefix + test_name, value) # whether it needs it or not

    kmq_choice = {}
    kmq_choice[test_name] = value

    _kmq.push ["set", kmq_choice]

    console.log "Set A/B test '#{test_name}' to value '#{value}'"

  setup_tests: () =>
    for test_name, options of @test_definitions()
      if $.cookie(cookie_prefix + test_name) is null
        @choose_and_set_option(test_name)
      else
        console.log "Found A/B test value for '#{test_name}'"
        @set_option(test_name, $.cookie(cookie_prefix + test_name))

  choose_and_set_option: (test_name) =>
    options = @test_definitions()[test_name]
    i = randInt(options.length)
    @set_option(test_name, options[i])

window.CircleABTestVM = new ABTestViewModel

ko.bindingHandlers.ab_test =
  init: (el, valueAccessor) =>
    test = valueAccessor().test
    option = valueAccessor().option

    try
      ab_tests = window.CircleABTestVM.ab_tests()

      display_p = ab_tests[test] is option

    catch error
      console.log "Error setting the A/B test for test '#{test}' and option '#{option}'"
      console.log error
      display_p = false

    console.log "Displaying option '#{option}' for test '#{test}':", display_p

    if not display_p then $(el).css('display', 'none')
