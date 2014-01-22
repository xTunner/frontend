# 1. Don't define a test with null as one of the options
# 2. If you change a test's options, you must also change the test's name
#
# You can add overrides, which will set the option if override_p returns true.

exports = this

exports.ab_test_definitions =
  a_is_a: [true, false]
  pricing_maintain_infrastructure: [true, false]
  customer_logos: [true, false]
  github_modal: [true, false]
