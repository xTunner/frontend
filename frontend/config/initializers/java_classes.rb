# Be sure to restart your server when you modify this file.

# This adds the classes/ directory as a classpath for the rails app.
require 'java'
$CLASSPATH << File.join(Rails.root, "classes")
