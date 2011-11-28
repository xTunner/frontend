# There are lots of way's of calling backend code, this allows us to mock it

# clj = JRClj.new
# clj.inc 0

# circle = JRClj.new "circle.init"

# db = JRClj.new "circle.db"
# db.run "circle.db/init"

# circle.run "circle.init/-main"
# circle.init

# JRClj.new("circle.util.time").ju_now

class Backend
  class_attribute :mock

  # Start the backend, by calling circle.init/init, and setting up the right directory
  def self.initialize
    return if Backend.mock

    clj = JRClj.new "circle.init"
    clj.maybe_change_dir
    clj.init
  end

  def self.github_hook(url, after, ref, json)
    return if Backend.mock
    self.initialize

    clj = JRClj.new "circle.hooks"
    clj.github url, after, ref, json
  end

  def self.build(project)
    return if Backend.mock
    self.initialize

    clj = JRClj.new "circle.hooks"
    clj.run_build_from_jruby(project.name, "build")
  end
end

Backend.mock = true
if RUBY_PLATFORM == 'java' || Rails.env != 'test' then
  Backend.mock = false
end
