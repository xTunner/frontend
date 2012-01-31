require 'spec_helper'

describe Build do

  it "should return a username for an email address" do
    b = Factory(:build)
    b.committer_handle.should == "user"
  end
end
