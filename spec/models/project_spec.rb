require 'spec_helper'

describe Project do

  it "should give us the project name from github url" do
    project = Factory(:project)
    project.github_project_name.should == "circleci/circle-dummy-project"

    project = Factory(:project, :vcs_url => "https://github.com/asd/asd")
    project.github_project_name.should == "asd/asd"
  end

  it "should fetch the same project" do
    project = Factory(:project)

    Project.from_github_name(project.github_project_name).should == project
    Project.from_url(project.vcs_url).should == project
  end

  it "should get the correct build" do
    pending
  end

end
