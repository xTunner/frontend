require File.expand_path('../boot', __FILE__)

require "action_controller/railtie"
require "action_mailer/railtie"
require "active_resource/railtie"
require 'sprockets/railtie'


if RUBY_PLATFORM == 'java'
  # Load clojure jars. clojure.jar needs to be on the classpath before the jrclj gem is loaded.
  require 'java'
  root_dir = "#{File.dirname(__FILE__)}/../"
  $CLASSPATH << "#{root_dir}/src/"
  $CLASSPATH << "#{root_dir}/test/"
  Dir["#{root_dir}/jars/*.jar"].each do |jar|
    require jar
  end
  Dir["#{root_dir}/lib/dev/*.jar"].each do |jar|
    require jar
  end

else
  raise if (Rails.env == "production" or Rails.env == "staging")
end


if defined?(Bundler)
  # If you precompile assets before deploying to production, use this line
  Bundler.require(*Rails.groups(:assets => %w(development test)))
  # If you want your assets lazily compiled in production, use this line
  # Bundler.require(:default, :assets, Rails.env)
end

module MongoidTest
  class Application < Rails::Application
    # Settings in config/environments/* take precedence over those specified here.
    # Application configuration should go into files in config/initializers
    # -- all .rb files in that directory are automatically loaded.

    # Custom directories with classes and modules you want to be autoloadable.
    config.autoload_paths += %W(#{config.root}/lib #{config.root}/spec)

    # Only load the plugins named here, in the order given (default is alphabetical).
    # :all can be used as a placeholder for all plugins not explicitly named.
    # config.plugins = [ :exception_notification, :ssl_requirement, :all ]

    # Activate observers that should always be running.
    # config.active_record.observers = :cacher, :garbage_collector, :forum_observer

    # Set Time.zone default to the specified zone and make Active Record auto-convert to this zone.
    # Run "rake -D time" for a list of tasks for finding time zone names. Default is UTC.
    # config.time_zone = 'Central Time (US & Canada)'

    # The default locale is :en and all translations from config/locales/*.rb,yml are auto loaded.
    # config.i18n.load_path += Dir[Rails.root.join('my', 'locales', '*.{rb,yml}').to_s]
    # config.i18n.default_locale = :de


    # Allow FactoryGirl to reload properly
    # http://blog.thefrontiergroup.com.au/2011/03/reloading-factory-girl-factories-in-the-rails-3-console/
    ActionDispatch::Callbacks.after do
      # TECHNICAL_DEBT: this doesn't work, and slows me down every time I need
      # to reload a factory and must restart the server
      # Reload the factories
      if Rails.env.test?
        unless FactoryGirl.factories.blank? # first init will load factories, this should only run on subsequent reloads
          puts "Reloading factories"
          FactoryGirl.factories.clear
          FactoryGirl.find_definitions
        end
      end
    end

    # Configure the default encoding used in templates for Ruby 1.9.
    config.encoding = "utf-8"

    # Configure sensitive parameters which will be filtered from the log file.
    config.filter_parameters += [:password, :password_confirmation]

    # Enable the asset pipeline
    config.assets.enabled = true

    # Version of your assets, change this if you want to expire all your assets
    config.assets.version = '1.0'

    config.after_initialize do
      # Initialize the backend early, before the server is being used to satisfy user requests, so that
      # users never suffer the >1m startup times.
      if RUBY_PLATFORM == 'java'
        Backend.initialize
      end

      bot = User.where(:email => "bot@circleci.com").first()

      if not bot
        bot = User.new(:email => "bot@circleci.com", :password => "brick amount must thirty")
      end
      bot.admin = true
      bot.save!
    end
  end
end
