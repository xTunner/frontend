# Caveats:
#   Don't set null as an option.
#   You can't change the options once they're set, you need to define a new test
#
# Example setup in viewModel:
#   @ab = new(ABTests({daniel_test: ["option1", "option2"]})).ab_tests
#
# Example usage in view:
#   %p{href: "#", data-bind: "if: AB.daniel_test == true"}
#     This is the text that will show up if option is set to true
#   %p{href: "#", data-bind: "if: AB.daniel_test == false"}
#     This is the text that will show up if option is set to false

exports = this

randInt = (n) ->
  Math.floor(Math.random() * n)

class exports.ABTests
  constructor: (test_definitions, options) ->
    options or= {}

    @cookie_prefix = options.cookie_prefix || "ab_test_"

    # defs don't need to be an observable, but we may want to do
    # inference in the future. Better to set it up now.
    @test_definitions = ko.observable(test_definitions)

    # @ab_tests is an object with format {'test_name': "chosen_option", ...}
    # Again, no need to be observable yet
    @ab_tests = ko.observable({})

    @setup_tests()

  set_option: (test_name, value) =>
    tests = @ab_tests()
    tests[test_name] = value

    @ab_tests(tests)

    $.cookie @cookie_prefix + test_name, value,
      expires: 365
      path: "/"
      json: true

    kmq_choice = {}
    kmq_choice[test_name] = value

    _kmq.push ["set", kmq_choice]

    console.log "Set (or reseting) A/B test '#{test_name}' to value '#{value}'"

  setup_tests: () =>
    for test_name, options of @test_definitions()
      if $.cookie(@cookie_prefix + test_name) is null
        @choose_and_set_option(test_name)
      else
        console.log "Found A/B test value for '#{test_name}'"
        @set_option(test_name, $.cookie(@cookie_prefix + test_name))

  choose_and_set_option: (test_name) =>
    options = @test_definitions()[test_name]
    i = randInt(options.length)
    @set_option(test_name, options[i])
