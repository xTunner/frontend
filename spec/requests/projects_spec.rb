require 'spec_helper'

describe "Projects", :js => true do
  describe "edit page" do

    before(:each) do
      host = "circlehost:3001"
      host! host
      Capybara.app_host = "http://" + host
      @user = login(:user)
    end

    #TECHNICAL_DEBT: this is slow, just log in once per user/project
    def login(user_symbol)
      user = Factory.create(user_symbol)
      visit '/login'
      fill_in "user_email", :with => user.email
      fill_in "user_password", :with => user.password
      click_button "Sign in"
      return user
    end

    def _fill_in(target, options={})
      fill_in target, options
      # Backbone.modelbinding relies on change events, which for some inputs only
      # trigger when the input loses focus. But capybara sometimes forgets to
      # trigger them, I'm not sure why.
      page.execute_script("$('##{target}').trigger('change');")
    end

    def trigger_change_event(target)
      page.execute_script("$('##{target}').trigger('change');")
    end


    def visit_project(project_symbol)
      @project = FactoryGirl.create project_symbol
      @user.projects << @project
      @user.save

      visit root_path
      page.should have_link(@project.github_project_name)

      click_link @project.github_project_name
      click_link "Edit"
    end


    def goto_hash(name)
      click_link name.capitalize
      URI.parse(current_url).path.should == "/gh/#{@project.github_project_name}/edit"
      URI.parse(current_url).fragment.should == name
    end


    it "should have the contents of the project entered already" do
      visit_project :project_with_specs
      goto_hash "setup"
      find_field("setup").value.should == "echo do setup"
      find_field("dependencies").value.should == "echo do dependencies"

      goto_hash "tests"
      find_field("test").value.should == "echo do test"
      find_field("extra").value.should == "echo do extra"
    end

    it "should keep the contents when switching between sections" do
      visit_project :project_with_specs
      goto_hash "setup"
      fill_in "setup", :with => "echo a different command"
      trigger_change_event "setup"


      goto_hash "tests"
      goto_hash "setup"
      find_field("setup").value.should == "echo a different command"
    end

    it "should have sane URLs despite clicking around" do
      visit_project :project_with_specs
      goto_hash "settings"
      goto_hash "tests"
      goto_hash "setup"
      goto_hash "setup"
      goto_hash "settings"
      goto_hash "tests"
      goto_hash "settings"
      goto_hash "tests"
    end

    it "should be saved when we click save" do
      visit_project :project_with_specs

      goto_hash "setup"
      fill_in "setup", :with => "echo c"
      trigger_change_event "setup"
      fill_in "dependencies", :with => "echo d"
      trigger_change_event "dependencies"

      click_link "Now set up your tests =>"

      fill_in "test", :with => "echo a"
      trigger_change_event "test"
      fill_in "extra", :with => "echo b"
      trigger_change_event "extra"

      click_button "Save commands and run your tests"

      sleep 0.5 # wait for the PUT to save
      p = Project.from_url(@project.vcs_url)

      p.test.should == "echo a"
      p.extra.should == "echo b"
      p.setup.should == "echo c"
      p.dependencies.should == "echo d"

    end

    it "should not have the same contents on reload" do
    end

    # it "should display an inferred project properly" do
    # end

    # it "should display a disabled project properly" do
    # end

    # it "should display an overridden project properly" do
    # end

    # it "should show a running test after saving/starting a build" do
    # end
  end
end
