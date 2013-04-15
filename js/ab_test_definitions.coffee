# 1. Don't define a test with null as one of the options
# 2. If you change a test's options, you must also change the test's name
#
# You can add overrides, which will set the option if override_p returns true.

exports = this

exports.ab_test_definitions =
  options:
    quick_setup_or_trial: ["14-day free trial.", "Run your first test with 3 clicks."]
    github_warning_modal: [true, false]
    show_add_repos_blank_slate: [true, false] # true shows the show_add_repos_blank_slate div, false hides it
    landing_page_button: ["RUN YOUR TESTS","RUN YOUR TESTS. NO CREDIT CARD REQUIRED"]
  overrides:
    [
      override_p: ->
        window.circleEnvironment is 'test'
      options:
        github_warning_modal: false
    ]
