module WorkerMacros
  def use_workers
    after(:each) do
      old = Backend.mock
      Backend.mock = false
      Backend.wait_for_all_workers
      Backend.mock = old
    end
  end

  def disable_mocking
    before :each do
      Backend.mock = false
    end

    after :each do
      Backend.mock = true
    end
  end

  def use_clojure_factories
    before :each do
      # Blocking workers skip the mock check
      Backend.blocking_worker "circle.test-utils/ensure-test-db"
    end
  end
end

RSpec.configure do |config|
  config.extend WorkerMacros, :type => :controller
end
