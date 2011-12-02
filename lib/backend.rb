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

  def self.github_hook(url, after, ref, json)
    self.run_worker "github", url, after, ref, json
  end

  def self.build(project)
    self.run_worker "run-build-from-jruby", project.name, "build"
  end

  # We launch workers using run_worker. On the clojure side, we use futures to launch the job.
  # The future is stored in a hash, indexed by integer. We return that integer to Ruby, where
  # we can then query it using check_worker, or get the value using resolve_worker. Note that
  # resolve_worker only returns the value once! So this is roughly equivalent to a proper
  # queue.
  def self.run_worker(name, *args)
    return 0 if Backend.mock

    clj = JRClj.new "circle.workers"
    fn = RT.var("circle.workers", name)
    clj.run_worker(fn, args)
  end

  def self.check_worker(id)
    return true if Backend.mock

    clj = JRClj.new "circle.workers"
    clj.check_worker(id)
  end

  def self.resolve_worker(id)
    return Nil if Backend.mock

    clj = JRClj.new "circle.workers"
    clj.resolve_worker(id)
  end


  # Start the backend, by calling circle.init/init, and setting up the right directory.
  def self.initialize
    return if Backend.mock

    clj = JRClj.new "circle.init"
    clj.maybe_change_dir
    clj.init
  end

end

Backend.mock = true
if RUBY_PLATFORM == 'java' || Rails.env != 'test' then
  Backend.mock = false
end

at_exit do
  JRClj.new("clojure.core").shutdown_agents
end
