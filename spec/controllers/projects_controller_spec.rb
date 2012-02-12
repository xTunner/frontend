require 'spec_helper'

describe ProjectsController do
  login_user # don't forget you can use current_user
  uses_workers


  it "should render using pretty 'gh' links" do
    project = FactoryGirl.create :project
    github_project_path(project).should == '/gh/' + project.github_project_name
  end

  it "shouldn't be able to render unowned projects" do
    project = FactoryGirl.create :unowned_project
    lambda {
      get 'show', :project => project.github_project_name
    }.should raise_error(CanCan::AccessDenied)
  end

  it "should be able to access owned projects" do
    project = FactoryGirl.create :project
    subject.current_user.projects << project
    get 'show', :project => project.github_project_name
  end

  it "should work for weird characters in the project url" do
    github_project_path(FactoryGirl.create(:project_with_weird_characters))
  end

  describe "json tests" do
    let!(:project) { Project.unsafe_create :vcs_url => "https://github.com/a/b", :users => [subject.current_user] }
    before(:each) do
      request.env["HTTP_ACCEPT"] = "application/json"
    end

    after(:each) do
      response.should be_success
    end

    it "should start a build when the page is edited" do
      Backend.mock = false

      put :update, { :project => "a/b", "setup" => "" }.as_json
      Backend.wait_for_all_workers

      project.latest_build.why.should == "edit"
      project.latest_build.user.should == subject.current_user

      Backend.mock = true
    end

    it "should start a hipchat notification worker" do
      old = Backend.worker_count
      put :update, { :project => "a/b", "hipchat_room" => "test" }
      Backend.worker_count.should == old + 1
    end
  end
end
