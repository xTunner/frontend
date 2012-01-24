MongoidTest::Application.configure do
  # Settings specified here will take precedence over those in config/application.rb

  # Code is not reloaded between requests
  config.cache_classes = true

  # Full error reports are disabled and caching is turned on
  config.consider_all_requests_local       = false
  config.action_controller.perform_caching = true

  # Disable Rails's static asset server (Apache or nginx will already do this)
  # TODO: move this back to false after setting nginx to serve the assets.
  config.serve_static_assets = true

  # Compress JavaScripts and CSS
  config.assets.compress = true

  # Don't fallback to assets pipeline if a precompiled asset is missed
  config.assets.compile = false

  # Generate digests for assets URLs
  config.assets.digest = true

  # Defaults to Rails.root.join("public/assets")
  # config.assets.manifest = YOUR_PATH

  # Specifies the header that your server uses for sending files
  # config.action_dispatch.x_sendfile_header = "X-Sendfile" # for apache
  # config.action_dispatch.x_sendfile_header = 'X-Accel-Redirect' # for nginx

  # Force all access to the app over SSL, use Strict-Transport-Security, and use secure cookies.
  # config.force_ssl = true

  # See everything in the log (default is :info)
  # config.log_level = :debug

  # Use a different logger for distributed setups
  # config.logger = SyslogLogger.new

  # Use a different cache store in production
  # config.cache_store = :mem_cache_store

  # Enable serving of images, stylesheets, and JavaScripts from an asset server
  # config.action_controller.asset_host = "http://assets.example.com"


  # TECHNICAL_DEBT: Instead of all these separate files, we should merge them
  # all into one using the =require functionality in the asset pipeline. We
  # didn't do this at the start because of namespacing, but we should do it
  # ASAP. Until we do it, each file listed here will be fetched using a separate
  # HTTP request, and - worse - each file we use needs to be added to this line
  # in both staging.rb and production.rb (and, of course, we won't notice in
  # development because it uses live asset precompilation!). After adding the
  # line, you must do a RAILS_ENV=staging rake assets:precompile if you wish to
  # see this in a staging environment.

  # Precompile additional assets (application.js, application.css, and all non-JS/CSS are already added)
  config.assets.precompile += %w(
                                  backbone/mongoid_test.js
                                  controller-specific/**/*.js
                                  controller-specific/*.js
                                  home.js
                                  live.js
                                  wufoo.js
                                  controller-specific/**/*.css
                                  controller-specific/*.css
                                  home/*.css
                                  shared/*.css
                                )

  # ActionMailer Config: staging - do not perform deliveries
  config.action_mailer.delivery_method = :test
  config.action_mailer.default_url_options = { :host => 'staging.circleci.com' }
  config.action_mailer.perform_deliveries = false
  config.action_mailer.raise_delivery_errors = false
  config.action_mailer.default :charset => "utf-8"

  # Enable threaded mode
  # config.threadsafe!

  # Enable locale fallbacks for I18n (makes lookups for any locale fall back to
  # the I18n.default_locale when a translation can not be found)
  config.i18n.fallbacks = true

  # Send deprecation notices to registered listeners
  config.active_support.deprecation = :notify
end
