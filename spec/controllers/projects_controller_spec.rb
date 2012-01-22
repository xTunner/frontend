require 'spec_helper'

describe ProjectsController do
  login_user # don't forget you can use current_user

  it "should render using pretty 'gh' links" do
    project = subject.current_user.projects[0]
    github_project_path(project).should == '/gh/' + project.github_project_name
  end

  it "shouldn't be able to render unowned projects" do
    project = FactoryGirl.create(:unowned_project)
    lambda {
      get 'show', :project => project.github_project_name
    }.should raise_error(CanCan::AccessDenied)
  end

  it "should be able to access owned projects" do
    project = subject.current_user.projects[0]
    get 'show', :project => project.github_project_name
  end


  it "should work for weird characters in the project url" do
    github_project_path(FactoryGirl.create(:project_with_weird_characters))
  end
end
