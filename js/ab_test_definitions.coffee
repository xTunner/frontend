# 1. Don't define a test with null as one of the options
# 2. If you change a test's options, you must also change the test's name

exports = this

exports.ab_test_definitions =
  quick_setup_or_trial: ["14-day free trial.", "Run your first test with 3 clicks."]
  new_landing_page: [false, true] # in the views, a is false, b is true
  github_warning_modal: [true, false]
