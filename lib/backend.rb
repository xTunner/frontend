# TECHNICAL_DEBT: this stuff is not tested at all
require 'jruby'

class Backend

  # TODO: refactor this until it's transparent
  def self.github_hook(json)
    self.fire_worker "circle.workers.github/start-build-from-hook", json
  end

  def self.build(project, why, who, inferred=false)
    self.fire_worker "circle.workers.website/run-build-from-jruby", project.vcs_url, inferred, why, who.id.to_s
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
    if Rails.env.development?
      reload = keyword.invoke("reload") # reload the source automatically
      require.invoke(symbol.invoke(package), reload)
    else
      require.invoke(symbol.invoke(package))
    end

    # Actually fetch it.
    RT.var(package, function)
  end

  def self.fire_worker(name, *args)
    return self.mocked_fire_worker(name, *args) if self.mock

    # TODO: need to coerce args to clj types (it's fine for now
    # because Strings and ints are the same in both)
    fn = self._fn name
    Backend.clj.fire_worker(fn, *args)
  end

  def self.start_worker(name, *args)
    return self.mocked_start_worker(name, *args) if self.mock

    fn = self._fn name
    Backend.clj.start_worker(fn, *args)
  end

  def self.blocking_worker(name, *args)
    # We don't mock this since it will almost always be a short command
    fn = self._fn name
    Backend.clj.blocking_worker(fn, *args)
  end

  def self.worker_done?(id)
    return self.mocked_worker_done?(id) if self.mock

    Backend.clj.worker_done?(id)
  end

  def self.wait_for_worker(id)
    return self.mocked_wait_for_worker(id) if self.mock

    Backend.clj.wait_for_worker(id)
  end

  def self.worker_count
    return self.mocked_worker_count if self.mock

    Backend.clj.worker_count
  end

  def self.wait_for_all_workers
    return if self.mock

    Backend.clj.wait_for_all_workers
  end


  def self.initialize
    if Backend._clj.nil?
      init_ns = JRClj.new("circle.init")
      JRClj.new("circle.env").set_env(Rails.env)
      init_ns.init()
      JRClj.new("circle.ruby").init(JRuby.runtime)

      Backend._clj = JRClj.new "circle.workers"
    end
  end

  def self.eager_initialize
    initialize
    # TODO walk all clojure ns
  end

  # Start the backend, by calling circle.init/init, and setting up the right directory.
  def self.clj # I'm not sure I get this, it can be nil after being initialized?
    initialize
    Backend._clj
  end

  class_attribute :mock
  class_attribute :_clj


  # Mocked interface (don't use a separate class because the Backend tests rely
  # on setting mocked to false)
  class MockedWorker
    class_attribute :next_id
    class_attribute :queue
    attr_accessor :id, :name, :args, :completed, :fired

    def initialize(name, fired, *args)
      @name = name
      @args = *args
      @completed = false
      @fired = fired
      @id = MockedWorker.next_id
      MockedWorker.next_id += 1
    end
  end
  MockedWorker.next_id = 1
  MockedWorker.queue = []


  def self.mocked_fire_worker(name, *args)
    puts "Mock fire_worker #{name}"
    MockedWorker.queue << MockedWorker.new(name, true, *args)
  end

  def self.mocked_start_worker(name, *args)
    puts "Mock start_worker #{name}"
    MockedWorker.queue << MockedWorker.new(name, false, *args)
    return MockedWorker.queue.last.id
  end

  def self.mocked_worker_done?(id)
    return true
  end

  def self.mocked_wait_for_worker(id)
    # not the same as done!
    MockedWorker.queue[id].completed = true
    return nil
  end

  def self.mocked_worker_count
    return MockedWorker.queue.length
  end
end

Backend.mock = true
if RUBY_PLATFORM == 'java' && !Rails.env.test? then
  Backend.mock = false
end

at_exit do
  JRClj.new("clojure.core").shutdown_agents
end
