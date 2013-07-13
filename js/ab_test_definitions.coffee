# 1. Don't define a test with null as one of the options
# 2. If you change a test's options, you must also change the test's name
#
# You can add overrides, which will set the option if override_p returns true.

exports = this

exports.ab_test_definitions =
  options:
    github_warning_modal: [true, false]
    home_red_buttons: [true, false]
    home_text_hero: [true, false]
    home_extra_ctas: [true, false]
    home_cta_plan_price_trial: [true, false] # around the home button CTA, include text about plans starting from $19, and 14 day trial
    number_one_instead_of_happy_customers_callout: [true, false]
    home_speed_level: ["4-way", "8-way", "12-way"]

  overrides:
    [
      override_p: ->
        window.circleEnvironment is 'test'
      options:
        github_warning_modal: false
    ]
