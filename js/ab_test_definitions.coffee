# 1. Don't define a test with null as one of the options
# 2. If you change a test's options, you must also change the test's name
#
# You can add overrides, which will set the option if override_p returns true.

exports = this

exports.ab_test_definitions =
  options:
    github_warning_modal: [true, false]
    home_speed_level: ["4-way", "8-way", "12-way"]
    stripe_v3: [true, false]
    new_outer_old_copy: [true, false]
    first_cta_button_text: ["A", "B", "C"]
    pricing_maintain_infrastructure: [true, false]
    a_is_a: [true, false]

  overrides:
    [
      override_p: ->
        window.circleEnvironment is 'test'
      options:
        github_warning_modal: false
        stripe_v3: false
        use_ks_outer: false
        pricing_maintain_infrastructure: false
    ]
