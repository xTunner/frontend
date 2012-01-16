require 'spec_helper'

describe "Projects" do
  describe "edit" do
    it "should have a " do
      project = Factory.create!(:project)

      login_user
      visit root_path
      page.should have_link("circleci/circle-dummy-project")
    end
  end
end
