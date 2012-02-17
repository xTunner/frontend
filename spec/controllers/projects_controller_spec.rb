require 'spec_helper'

describe ProjectsController do
  use_clojure_factories
  login_user
  use_workers

  it "should render using pretty 'gh' links" do
    project = Project.first
    github_project_path(project).should == '/gh/' + project.github_project_name
  end

  it "shouldn't be able to render unowned projects" do
    project = Project.first
    project.users = []
    project.save!

    lambda {
      get 'show', :project => project.github_project_name
    }.should raise_error(CanCan::AccessDenied)
  end

  it "should be able to access owned projects" do
    project = Project.first
    subject.current_user.projects << project
    get 'show', :project => project.github_project_name
  end

  it "should work for weird characters in the project url" do
    project = Project.unsafe_create :vcs_url => "https://github.com/._-_.-/-.__-.-/"
    github_project_path(project)
  end

  describe "json tests" do
    disable_mocking

    before(:each) do
      request.env["HTTP_ACCEPT"] = "application/json"
    end

    after(:each) do
      response.should be_success
    end

    it "should start a build when the page is edited" do
      project = Project.from_url "https://github.com/arohner/circle-dummy-project"
      put :update, { :project => "arohner/circle-dummy-project", "setup" => "" }.as_json
      Backend.wait_for_all_workers

      project.latest_build.why.should == "edit"
      project.latest_build.user.should == subject.current_user
    end

    it "should start a hipchat notification worker" do
      old = Backend.worker_count
      put :update, { :project => "arohner/circle-dummy-project", "hipchat_room" => "test" }
      Backend.worker_count.should == old + 1
    end
  end
end
