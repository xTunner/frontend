require 'spec_helper'

describe ApplicationHelper do

  describe "as_commit_time" do
    it "should return known values" do
      Time.stub!(:now).and_return(Time.utc(2011, 12, 21, 19, 28, 32)) # party like it's Wednesday 21th December, 2011

      as_commit_time(Time.utc(2010, 12, 15, 16, 10, 13)).should == "Dec 15, 2010"
      as_commit_time(Time.utc(2011, 1, 6, 14, 0, 13)).should == "Jan 6 at 2:00pm"
      as_commit_time(Time.utc(2011, 12, 12, 16, 10, 59)).should == "Last Monday (Dec 12) at 4:10pm"
      as_commit_time(Time.utc(2011, 12, 19, 11, 45, 45)).should == "Monday at 11:45am"
      as_commit_time(Time.utc(2011, 12, 20)).should == "Yesterday at 12:00am"
      as_commit_time(Time.utc(2011, 12, 21, 18, 0, 13)).should == "an hour ago (6:00pm)"
      as_commit_time(Time.utc(2011, 12, 21, 17, 0, 13)).should == "2 hours ago (5:00pm)"
      as_commit_time(Time.utc(2011, 12, 21, 10, 15, 13)).should == "10:15am"
      as_commit_time(Time.utc(2011, 12, 21, 19, 18, 13)).should == "10 minutes ago"
      as_commit_time(Time.utc(2011, 12, 21, 19, 28, 30)).should == "2 seconds ago"
    end
  end

  describe "revision_link_to" do
    let(:build) { Build.unsafe_create(:vcs_url => "https://github.com/arohner/CircleCI", :vcs_revision => "b833cd06910ee92b6f9327261bcf4bd8f97200a8") }
    it "should work for a simple url" do
      revision_link_to(build).should == "<a href='https://github.com/arohner/CircleCI/commit/b833cd06910ee92b6f9327261bcf4bd8f97200a8'>b833cd069<img src='/assets/octocat-tiny.png'></a>"
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
