var path = require('path');

module.exports = function(config) {
  var root = 'resources/public/cljs/test';

  config.set({
    frameworks: ['cljs-test'],

    files: [
      // Provide a stub `window.renderContext`
      'test-js/test-render-context.js',

      'resources/public/assets/js/om-dev*.js',

      path.join(root, 'goog/base.js'),


      // Load the app and tests.
      path.join(root, 'frontend-test.js'),
    ],

    client: {
      // main function
      args: ['circle.karma.run_tests_for_karma']
    },

    browsers: ['Chrome'],

    reporters: ['progress']
  });
};
