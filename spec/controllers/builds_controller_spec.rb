require 'spec_helper'

describe BuildsController do
  login_user # don't forget you can use current_user
  uses_workers

  let!(:project) { Project.unsafe_create :vcs_url => "https://github.com/a/b", :users => [subject.current_user] }

  it "should trigger a build with a known user" do
    Backend.mock = false
    post :create, :project => "a/b"
    Backend.wait_for_all_workers

    b = Build.first
    b.vcs_url.should == project.vcs_url
    b.user.should == subject.current_user
    b.why.should == "trigger"
    b.as_html_instant_message.should =~ /triggered by Test User/
    Backend.mock = true
  end
end
