require 'spec_helper'

describe "Projects", :js => true do
  describe "edit page" do
    let(:user) { User.create(:name => "Test User", :email => "user@test.com", :password => "please") }

    let(:vcs_url) { "https://github.com/a/b" }
    let(:simple_project) { Project.unsafe_create(:vcs_url => vcs_url, :users => [user]) }
    let(:project_with_specs) { Project.unsafe_create(:vcs_url => vcs_url,
                                          :users => [user],
                                          :setup => "echo do setup",
                                          :dependencies => "echo do dependencies",
                                          :compile => "echo do compile",
                                          :test => "echo do test",
                                          # extra is a new field, but no-one put
                                          # anything in compile so we're fine.
                                          :extra => "echo do extra") }


    before(:each) do
      host = "circlehost:3001"
      host! host
      Capybara.app_host = "http://" + host
      user = login(:user)
    end

    #TECHNICAL_DEBT: this is slow, just log in once per user/project
    def login(user_symbol)
      visit '/login'
      fill_in "user_email", :with => user.email
      fill_in "user_password", :with => user.password
      click_button "Sign in"
      return user
    end

    def _fill_in(target, options={})
      fill_in target, options
      # Backbone.modelbinding relies on change events, which for some inputs
      # only trigger when the input loses focus. But capybara sometimes forgets
      # to trigger them, I'm not sure why. The real solution is for
      # backbone.modelbinding to use keyup events anyway.
      page.execute_script("$('##{target}').trigger('change');")
    end

    def expect_fragment(fragment)
      URI.parse(current_url).path.should == "/gh/#{@project.github_project_name}/edit"
      URI.parse(current_url).fragment.should == fragment
      lambda { # this shouldnt be a link
        find_link("##{fragment}")
      }.should raise_error Capybara::ElementNotFound
    end

    def wait_for_project(options={})
      start_time = Time.now
      while true do
        (Time.now - start_time).should < 1.seconds
        p = Project.from_url @project.vcs_url
        return p if (p[options.keys[0]] == options.values[0])
      end
    end



    def visit_project(project)
      @project = project
      user.projects << @project
      user.save

      visit root_path
      page.should have_link(@project.github_project_name)

      click_link @project.github_project_name
      click_link "Edit"
    end


    def goto_setup
      click_link "Test machine"
      expect_fragment "setup"
    end

    def goto_tests
      click_link "Test suite"
      expect_fragment "tests"
    end

    def goto_settings
      click_link "Test settings"
      expect_fragment "settings"
    end


    it "should have the contents of the project entered already" do
      visit_project project_with_specs
      goto_setup
      find_field("setup").value.should == "echo do setup"
      find_field("dependencies").value.should == "echo do dependencies"

      goto_tests
      find_field("test").value.should == "echo do test"
      find_field("extra").value.should == "echo do extra"
    end

    it "should keep the contents when switching between sections" do
      visit_project project_with_specs
      goto_setup
      _fill_in "setup", :with => "echo a different command"

      goto_tests
      goto_setup
      find_field("setup").value.should == "echo a different command"
    end

    it "should have sane URLs despite clicking around" do
      visit_project project_with_specs
      goto_settings
      goto_tests
      goto_setup
      goto_setup
      goto_settings
      goto_tests
      goto_settings
      goto_tests
    end

    it "should be saved when we click save" do
      visit_project project_with_specs

      goto_setup
      _fill_in "setup", :with => "echo c"
      _fill_in "dependencies", :with => "echo d"

      click_link "Now set up your tests =>"

      _fill_in "test", :with => "echo a"
      _fill_in "extra", :with => "echo b"

      click_button "Save commands and run your tests"

      p = wait_for_project :test => "echo a"
      p.test.should == "echo a"
      p.extra.should == "echo b"
      p.setup.should == "echo c"
      p.dependencies.should == "echo d"

      expect_fragment "settings"
    end

    it "should not have the same contents on reload" do
      visit_project project_with_specs

      goto_setup
      _fill_in "setup", :with => "echo c"

      saved_url = current_url
      visit '/'

      visit saved_url
      find_field("setup").value.should == "echo do setup"
    end

    it "should clear the settings" do
      visit_project project_with_specs
      goto_settings
      click_button "Delete your commands and revert to inferred"

      # You're supposed to be able to put the remaining code in the pending
      # block, but that's actually breaking _other_ tests!
      p = wait_for_project :test => ""

      p.test.should == ""
      p.extra.should == ""
      p.setup.should == ""
      p.dependencies.should == ""
    end

    it "should move through the UI properly" do
      visit_project simple_project

      page.should have_content("Your project settings are automatically inferred!")
      click_link "Override automatic inferrence"

      expect_fragment "setup"

      pending
    end

    it "should show a running test after saving/starting a build" do
      pending
    end

  end
end
