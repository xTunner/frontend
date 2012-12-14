# Make sure _ab_test_definitions is defined. Either define it in this file,
# or define it window._ab_test_definitions. Format is 'test-name': [options...]
#
# Example test defs:
# _ab_test_definitions =
#   'daniel-test': [true, false]
#   'daniel-test-2': ['option1', 'option2', 'option3']
#
# Caveats:
#   Don't set null as an option.
#   You can't change the options once they're set, you need to define a new test
#   You shouldn't have another global variable named AB
#
# Example usage in view:
#   %p{href: "#", data-bind: "ab_test: {test: 'Show beta text', option: true}"}
#     This is the paragraph that will show up if option is set to true
#   %p{href: "#", data-bind: "ab_test: {test: 'Show beta text', option: false}"}
#     This is the paragraph that will show up if option is set to false
#
#   Or, if you prefer not to use built-in knockout bindings:
#     %p{href: "#", data-bind: "if: AB.daniel_test == true"}
#       This is the text that will show up if option is set to true
#     %p{href: "#", data-bind: "if: AB.daniel_test == false"}
#       This is the text that will show up if option is set to false
#
# Note that the ab_test binding adds display:none to non-matching test options
# This may be desirable or undesirable depending on your use case

exports = this

## Define your A/B tests here
_ab_test_definitions =
  day_vs_week: ['day', 'week']

cookie_prefix = "ab_test_"
$.cookie.json = true
$.cookie.defaults.expires = 365
$.cookie.defaults.path = "/"

randInt = (n) ->
  Math.floor(Math.random() * n)

class ABTestViewModel
  constructor: () ->
    # defs don't need to be an observable, but we may want to do
    # inference in the future. Better to set it up now.
    @test_definitions = ko.observable(_ab_test_definitions)

    # @ab_tests is an object with format {'test_name': "chosen_option", ...}
    # Again, no need to be observable yet
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

CircleABTestVM = new ABTestViewModel

# Global variable
exports.AB = CircleABTestVM.ab_tests()

ko.bindingHandlers.ab_test =
  init: (el, valueAccessor) =>
    test = valueAccessor().test
    option = valueAccessor().option

    try
      ab_tests = AB

      display_p = ab_tests[test] is option

    catch error
      console.log "Error setting the A/B test for test '#{test}' and option '#{option}'"
      console.log error
      display_p = false

    console.log "Displaying option '#{option}' for test '#{test}':", display_p

    if not display_p then $(el).css('display', 'none')
