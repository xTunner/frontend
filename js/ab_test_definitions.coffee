# 1. Don't define a test with null as one of the options
# 2. If you change a test's options, you must also change the test's name

exports = this

exports.ab_test_definitions =
  show_keeping_code_safe: [true, false]
