require 'spec_helper'

describe ProjectsController do
  login_user # don't forget you can use current_user

  it "should render using pretty 'gh' links" do
    project = subject.current_user.projects[0]
    github_project_path(project).should == '/gh/' + project.github_project_name
  end

  it "shouldn't be able to render unowned projects" do
    project = Factory(:unowned_project)
    lambda {
      get 'show', :id => project.id
    }.should raise_error(CanCan::AccessDenied)
  end

  it "should be able to access owned projects" do
    project = subject.current_user.projects[0]
    get 'show', :id => project.id
  end
end
