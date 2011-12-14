require 'spec_helper'
require 'backend'

describe Backend do

  it "can call a clojure.core function" do
    id = Backend.start_worker "+", 4, 5
    sleep 0.1
    Backend.worker_done?(id).should == true # shouldnt take long
    Backend.wait_for_worker(id).should == 9
  end

  it "can call a function outside clojure.core" do
    id = Backend.start_worker "circle.util.core/sha1", "The quick brown fox jumps over the lazy dog"
    Backend.wait_for_worker(id).should == "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12"
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

  it "should respond immediately" do
    Backend.blocking_worker("+", 4, 5).should == 9
  end

  it "should raise on missing function" do
    lambda {
      Backend.blocking_worker("non-existant-function", 4, 5)
    }.should raise_error
  end

  it "should pass and return strings properly" do
    Backend.blocking_worker("str", "a", "b").should == "ab"
  end

end
