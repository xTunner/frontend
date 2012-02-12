require 'spec_helper'

describe BuildsController do
  login_user # don't forget you can use current_user

  let!(:project) { Project.unsafe_create :vcs_url => "https://github.com/a/b", :users => [subject.current_user] }

  it "should trigger a build with a known user" do
    post :create, :project => "a/b"
    b = Build.first
    b.project.should == project
    b.vcs_url.should == project.vcs_url
    b.user.should == subject.current_user
    b.why.should == "trigger"
  end
end
