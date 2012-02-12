require 'spec_helper'

describe BuildsController do
  login_user # don't forget you can use current_user
  uses_workers

  let!(:project) { Project.unsafe_create :vcs_url => "https://github.com/arohner/circle-dummy-project", :users => [subject.current_user] }

  it "should trigger a build with a known user" do
    Backend.mock = false
    post :create, :project => "arohner/circle-dummy-project"
    Backend.wait_for_all_workers

    b = Build.first
    b.project.should == project
    b.vcs_url.should == project.vcs_url
    b.user.should == subject.current_user
    b.why.should == "trigger"
    b.as_html_instant_message.should =~ /triggered by Test User/
    Backend.mock = true
  end
end
