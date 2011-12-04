require 'spec_helper'
require 'backend'

describe Backend do

  it "can call a clojure.core function" do
    id = Backend.start_worker "+", 4, 5
    Backend.worker_done?(id).should == true # shouldnt take long
    Backend.wait_for_worker(id).should == 9
  end

  it "should raise if a worker is dereferenced twice" do
    id = Backend.start_worker "+", 3,4
    Backend.wait_for_worker(id)

    lambda {
      Backend.wait_for_worker(id)
    }.should raise_error
  end

  it "should raise if you try a worker which doesn't exist" do
    lambda {
      id = Backend.worker_done? -1
    }.should raise_error

    lambda {
      id = Backend.wait_for_worker -1
    }.should raise_error
  end
end
