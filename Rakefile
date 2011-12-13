#!/usr/bin/env rake
# Add your own tasks in files placed in lib/tasks ending in .rake,
# for example lib/tasks/capistrano.rake, and they will automatically be available to Rake.

require File.expand_path('../config/application', __FILE__)

MongoidTest::Application.load_tasks

##
## Note that we're using exec here, so this rake process is taken over
## by the jruby process after the first exec. (To get live printing, and not have the rake process run for the life of the server).
##

task :midje do
  Dir.chdir "backend"
  exec "lein midje"
end

namespace :production do
  task :server do
    exec "RAILS_ENV=\"production\" ./deploy.sh"
  end
end

namespace :staging do
  task :server do
    exec "RAILS_ENV=\"staging\" ./deploy.sh"
  end
end
