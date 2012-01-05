require 'factory_girl'

# NOTE: this file does not automatically reload in Rails server. Use load() or restart the server.

Factory.define :project do |p|
  p.vcs_url "https://github.com/circleci/circle-dummy-project"
end

Factory.define :unowned_project, :class => Project do |p|
  p.vcs_url "https://github.com/circleci/circle-dummy-project2"
end

Factory.define :build do |b|
  b.vcs_url "https://github.com/circleci/circle-dummy-project"
  b.vcs_revision "abcdef1234566789"
  b.start_time Time.now - 10.minutes
  b.stop_time Time.now
  b.build_num 1
  b.after_create { |x| Factory(:user) } # always make a user, and therefore a project
end

Factory.define :action_log do |l|
  l.exit_code 0
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
