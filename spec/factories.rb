require 'factory_girl'

# NOTE: this file does not automatically reload in Rails server. Use load() or restart the server.

Factory.define :user do |u|
  u.name 'Test User'
  u.email 'user@test.com'
  u.password 'please'
end

Factory.define :github_user, :class => User do |u|
  # This user doesnt have a username or email set up in their profile
  u.email 'vendors@circleci.com'
  u.password 'habit review loss loss'
  u.name 'Circle Dummy user'
end

Factory.define :signup do |s|
  s.email "test@email.com"
  s.contact "true"
end
