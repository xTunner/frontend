require 'spec_helper'

describe Project do

  it "should give us the project name from github url" do
    project = Factory(:project)
    project.github_project_name.should == "circleci/circle-dummy-project"

    project = Factory(:project, :vcs_url => "https://github.com/asd/asd")
    project.github_project_name.should == "asd/asd"
  end
end
