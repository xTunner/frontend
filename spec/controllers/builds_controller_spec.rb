require 'spec_helper'

describe BuildsController do
  login_user
  use_clojure_factories
  use_workers
  disable_mocking


  it "should trigger a build with a known user" do
    post :create, :project => "arohner/circle-dummy-project"
    Backend.wait_for_all_workers

    p = Project.from_github_name "arohner/circle-dummy-project"
    b = Build.first

    b.project.should == p
    b.vcs_url.should == p.vcs_url
    b.user.should == subject.current_user
    b.why.should == "trigger"
    b.as_html_instant_message.should =~ /triggered by Test User/
  end
end
