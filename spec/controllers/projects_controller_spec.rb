require 'spec_helper'

describe ProjectsController do
  login_user # don't forget you can use current_user
  render_views

  it "should render using pretty 'gh' links" do
    project = subject.current_user.projects[0]
    github_project_path(project).should == '/gh/' + project.github_project_name
  end
end
