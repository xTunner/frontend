source 'http://rubygems.org'

gem 'rails', '3.1.1'
gem 'sqlite3'

if RUBY_PLATFORM == 'java'
  gem 'jruby-openssl'
  gem 'jrclj', :git => "git://github.com/pbiggar/jrclj.git"
end

gem 'jquery-rails'
gem "rspec-rails", ">= 2.7.0", :group => [:development, :test]
gem "bson_ext", "~> 1.4"
gem "mongoid", "~> 2.3"
gem "devise", ">= 1.4.9"


# Gems used only for assets and not required
# in production environments by default.
group :assets do
  gem 'sass-rails',   '~> 3.1.4'
  gem 'coffee-rails', '~> 3.1.1'
  gem 'uglifier', '>= 1.0.3'
end

group :test do
  gem "cucumber-rails", ">= 1.1.1"
  gem "capybara", ">= 1.1.1"
  gem "database_cleaner", ">= 0.6.7"
  gem "mongoid-rspec", ">= 1.4.4"
  gem "factory_girl_rails", ">= 1.3.0"
  gem "launchy", ">= 2.0.5"
  gem "minitest"
  gem 'haml-rails'
  gem 'turn', :require => false  # Pretty printed test output
end

group :development do
  gem 'haml-rails'
  gem 'rake'
  gem 'hpricot'
  gem 'ruby_parser'
end


# Use unicorn as the web server
# gem 'unicorn'

# Deploy with Capistrano
# gem 'capistrano'

# To use debugger
# gem 'ruby-debug19', :require => 'ruby-debug'
