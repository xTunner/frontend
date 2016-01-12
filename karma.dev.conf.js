var path = require('path');

module.exports = function(config) {
  var root = 'resources/public/cljs/out';

  config.set({
    frameworks: ['cljs-test'],

    files: [
      // Provide a stub `window.renderContext`
      'test-js/test-render-context.js',

      'resources/public/assets/js/om-dev*.js',

      path.join(root, 'goog/base.js'),


      path.join(root, 'frontend-dev.js'),

      // Require the actual tests.
      'test-js/require-karma.js',

      // Serve the app. Use `included: false` so that Karma itself doesn't load
      // these files in the browser; we leave that to `goog.require()` and
      // namespace dependency resolution.
      { pattern: path.join(root, '**/*.js'), included: false},
      { pattern: path.join(root, '**/*.js.map'), included: false},
      { pattern: path.join(root, '**/*.cljs'), included: false}
    ],

    client: {
      // main function
      args: ['circle.karma.run_tests_for_karma']
    },

    browsers: ['Chrome'],

    reporters: ['progress']
  });
};
