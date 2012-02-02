# This file is copied to spec/ when you run 'rails generate rspec:install'
ENV["RAILS_ENV"] ||= 'test'
require File.expand_path("../../config/environment", __FILE__)
require 'rspec/rails'
require 'rspec/autorun'

require 'sauce'
require 'sauce/capybara'

Sauce.config do |config|
  config.username = "circle"
  config.access_key = "e73d06d7-5ed4-4728-aca6-2ce398bbc163"
  config.browser = "firefox"
  config.os = "Windows 2003"
  config.browser_version = "3.6."
end

# much higher timeout because EC2 is incredibly slow
Capybara.default_wait_time = 10

# uncomment this to run tests in sauce labs
#Capybara.default_driver = :sauce


# Requires supporting ruby files with custom matchers and macros, etc,
# in spec/support/ and its subdirectories.
Dir[Rails.root.join("spec/support/**/*.rb")].each {|f| require f}

RSpec.configure do |config|
  # == Mock Framework
  #
  # If you prefer to use mocha, flexmock or RR, uncomment the appropriate line:
  #
  # config.mock_with :mocha
  # config.mock_with :flexmock
  # config.mock_with :rr
  config.mock_with :rspec

  # Remove this line if you're not using ActiveRecord or ActiveRecord fixtures
  #config.fixture_path = "#{::Rails.root}/spec/fixtures"

  # If you're not using ActiveRecord, or you'd prefer not to run each of your
  # examples within a transaction, remove the following line or assign false
  # instead of true.
  #config.use_transactional_fixtures = true

  # If true, the base class of anonymous controllers will be inferred
  # automatically. This will be the default behavior in future versions of
  # rspec-rails.
  config.infer_base_class_for_anonymous_controllers = false

  if ENV["RAILS_ENV"] == "test"

    # Tests run the web server under port 3001, and github is configured to redirect here.
    Capybara.server_port = 3001

    # Clean the database for each test
    require 'database_cleaner'
    config.before(:suite) do
      DatabaseCleaner.strategy = :truncation
      DatabaseCleaner.orm = "mongoid"
    end

    config.before(:each) do
      DatabaseCleaner.clean
    end
  end
end
