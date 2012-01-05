require 'factory_girl'

# NOTE: this file does not automatically reload in Rails server. Use load() or restart the server.

Factory.define :project do |p|
  p.vcs_url "https://github.com/circleci/circle-dummy-project"
end

Factory.define :unowned_project, :class => Project do |p|
  p.vcs_url "https://github.com/circleci/circle-dummy-project2"
end

Factory.define :user do |u|
  u.name 'Test User'
  u.email 'user@test.com'
  u.password 'please'
  u.after_create { |x| Factory(:project, :users => [x]) }
end


Factory.define :github_user, :class => User do |u|
  # This user doesnt have a username or email set up in their profile
  u.email 'builds@circleci.com'
  u.password 'engine process vast trace'
  u.name 'Circle Dummy user'
end

Factory.define :admin_user, :parent => :user do |u|
  u.admin true
end

Factory.define :signup do |s|
  s.email "test@email.com"
  s.contact "true"
end
