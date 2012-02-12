module WorkerMacros
  def uses_workers
    after(:each) do
      Backend.wait_for_all_workers
    end
  end
end

RSpec.configure do |config|
  config.extend WorkerMacros, :type => :controller
end
