#!/usr/bin/env rake
# Add your own tasks in files placed in lib/tasks ending in .rake,
# for example lib/tasks/capistrano.rake, and they will automatically be available to Rake.

require File.expand_path('../config/application', __FILE__)

MongoidTest::Application.load_tasks

JRUBY_OPTS = "--1.9 -J-XX:+CMSClassUnloadingEnabled -J-XX:+UseConcMarkSweepGC -J-XX:MaxPermSize=256m -J-Xmx1024m"

##
## Note that we're using exec here, so this rake process is taken over
## by the jruby process after the first exec. (To get live printing, and not have the rake process run for the life of the server).
##

def jruby_fn(cmd)
  s = "JRUBY_OPTS=\"#{JRUBY_OPTS}\" jruby -S #{cmd}"
  exec s
end

task :test do
  jruby_fn "rspec spec/"
end

task :server do
  jruby_fn "rails server"
end

namespace :production do
  task :server do
    jruby_fn "trinidad --config -e production"
  end
end
  
