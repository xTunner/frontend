require 'spec_helper'

describe ApplicationHelper do

  describe "revision_link_to" do
    let(:build) { Build.unsafe_create(:vcs_url => "https://github.com/circleci/circle", :vcs_revision => "b833cd06910ee92b6f9327261bcf4bd8f97200a8") }
    it "should work for a simple url" do
      revision_link_to(build).should == "<a href='https://github.com/circleci/circle/commit/b833cd06910ee92b6f9327261bcf4bd8f97200a8'>" +
        "<img src='/assets/octocat-tiny.png'>" +
        "b833cd069" +
        "</a>"
    end
    it "should include a branch" do
      build.branch = "test"
      revision_link_to(build).should include "test"
    end
  end

  describe "as_length_of_build" do
    it "should work for a set of known values" do
      as_length_of_build(Time.utc(2011, 12, 21, 19, 28, 32), Time.utc(2011, 12, 21, 19, 28, 36)).should == "4s"
      as_length_of_build(Time.utc(2011, 12, 21, 19, 28, 32), Time.utc(2011, 12, 21, 19, 29, 12)).should == "40s"
      as_length_of_build(Time.utc(2011, 12, 21, 19, 28, 32), Time.utc(2011, 12, 21, 19, 30, 12)).should == "1m 40s"
      as_length_of_build(Time.utc(2011, 12, 21, 19, 28, 32), Time.utc(2011, 12, 21, 19, 38, 33)).should == "10m"
      as_length_of_build(Time.utc(2011, 12, 21, 19, 28, 32), Time.utc(2011, 12, 21, 20, 33, 12)).should == "1h 4m"
      as_length_of_build(Time.utc(2011, 12, 21, 19, 28, 32), Time.utc(2011, 12, 21, 22, 48, 34)).should == "3h 20m"
    end
  end
end
