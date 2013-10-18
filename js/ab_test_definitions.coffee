# 1. Don't define a test with null as one of the options
# 2. If you change a test's options, you must also change the test's name
#
# You can add overrides, which will set the option if override_p returns true.

exports = this

exports.ab_test_definitions =
  home_speed_level: ["4-way", "8-way", "12-way"]
  stripe_v3: [true, false]
  first_cta_button_text: ["A", "B", "C"]
  pricing_maintain_infrastructure: [true, false]
  a_is_a: [true, false]
