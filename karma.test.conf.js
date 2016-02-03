var path = require('path');

module.exports = function(config) {
  var root = 'resources/public/cljs/test';

  config.set({
    frameworks: ['cljs-test'],

    files: [
      // Provide a stub `window.renderContext`.
      'test-js/test-render-context.js',

      // Load the dependencies we manage outside of Closure.
      // NB: This file has nothing to do with Om.
      'resources/public/assets/js/om-dev*.js',

      // Add Google Closure.
      path.join(root, 'goog/base.js'),

      // Load the app and tests.
      path.join(root, 'frontend-test.js'),
    ],

    client: {
      // main function
      args: ['frontend.test_runner.run_tests_for_karma']
    },

    browsers: ['Chrome'],

    reporters: ['progress']
  });
};
