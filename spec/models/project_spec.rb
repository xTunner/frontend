require 'spec_helper'

describe Project do

  let(:project) { Project.unsafe_create :vcs_url => "https://github.com/circleci/circle-dummy-project" }

  it "should give us the project name from github url" do
    project.github_project_name.should == "circleci/circle-dummy-project"
  end

  it "should fetch the same project" do
    Project.from_github_name(project.github_project_name).should == project
    Project.from_url(project.vcs_url).should == project
  end

  it "should get the correct build" do
    pending
  end

end
