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

  # TODO: refactor this until it's transparent
  def self.github_hook(url, after, ref, json)
    self.fire_worker "circle.workers.github/start-build-from-hook", url, after, ref, json
  end

  def self.build(project)
    self.fire_worker "circle.workers/run-build-from-jruby", project.name, "build"
  end

  # We launch workers using start_worker. On the clojure side, we use futures to launch the job.
  # The future is stored in a hash, indexed by integer. We return that integer to Ruby, where
  # we can then query it using check_worker, or get the value using resolve_worker. Note that
  # resolve_worker only returns the value once! So this is roughly equivalent to a proper
  # queue.

  def self._fn(name)
    """name can include a single '/' and/or any number of '.'s"""
    (package, function) = name.split("/")
    if function.nil? then
      function = package
      package = "clojure.core"
    end
    raise "Error: no package" if package.empty?

    # Make sure the package is required, or we can't fetch the function
    require = RT.var("clojure.core", "require")
    symbol = RT.var("clojure.core", "symbol")
    keyword = RT.var("clojure.core", "keyword")
    reload = keyword.invoke("reload") # reload the source automatically
    require.invoke(symbol.invoke(package), reload)

    # Actually fetch it.
    RT.var(package, function)
  end

  def self.fire_worker(name, *args)
    return nil if Backend.mock
    # TODO: need to coerce args to clj types (it's fine for now
    # because Strings and ints are the same in both)

    fn = self._fn name
    Backend.clj.fire_worker(fn, *args)
  end

  def self.start_worker(name, *args)
    return 0 if Backend.mock

    fn = self._fn name
    Backend.clj.start_worker(fn, *args)
  end

  def self.worker_done?(id)
    return true if Backend.mock

    Backend.clj.worker_done?(id)
  end

  def self.wait_for_worker(id)
    return nil if Backend.mock

    Backend.clj.wait_for_worker(id)
  end

  def self.worker_count
    return 1 if Backend.mock

    Backend.clj.worker_count
  end


  # Start the backend, by calling circle.init/init, and setting up the right directory.
  def self.initialize
    return if Backend.mock

    Backend.clj = JRClj.new "circle.init", "circle.workers"
    Backend.clj.maybe_change_dir
    Backend.clj.init
  end

  class_attribute :mock
  class_attribute :clj
end

Backend.mock = true
if RUBY_PLATFORM == 'java' || Rails.env != 'test' then
  Backend.mock = false
end

at_exit do
  JRClj.new("clojure.core").shutdown_agents
end
