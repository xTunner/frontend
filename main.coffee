# Paths
require.config baseUrl: "assets"

require.config paths:
  # Libs
  outer: "js/outer.js.dieter"

  # Polyfills
  placeholder: "js/libs/placeholder"

# Main Application
require [ "outer" ], (App) ->
