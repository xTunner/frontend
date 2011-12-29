describe "Users" do

  describe "login" do

    it "should get the user page when it logs in" do
      user = Factory(:user)

      visit root_path
      click_link "Login"

      page.should have_content("Sign in")
      fill_in "user[email]", :with => user.email
      fill_in "user[password]", :with => user.password
      click_button "Sign in"

      page.should have_content("Signed in successfully.")
      page.should have_content("Your builds should now be working")
    end
  end

  describe "join" do

    before(:each)  do
      host = "circlehost:3001"
      host! host
      Capybara.app_host = "http://" + host
    end

    it "selenium works", :js => true do
    end

    it "straight pass through the form works", :js => true do
      # Create the user using build() so they wont be added to the DB.
      # Otherwise, the test will fail when we try to add a user.
      user = Factory.build(:github_user)

      visit join_path
      URI.parse(current_url).host.should == "circlehost" # sanity check
      URI.parse(current_url).port.should == 3001 # sanity check
      page.should have_content("We need GitHub access")
      click_link "Authorize us on GitHub"

      # If we reuse the test runner (say using the REPL and circle.ruby/rspec,
      # the cookies may not be cleared. This looks like a selenium bug:
      # https://github.com/jnicklas/capybara/issues/535. If we don't clear the
      # cookies, then we'll be redirected straight to the join page, and we
      # won't stop on github.
      if URI.parse(current_url).host == "github.com"
          fill_in "login", :with => "circleci"
          fill_in "password", :with => user.password
          click_button "Log in"
      end

      Capybara.default_wait_time = 5 # how long does it take for github to come back to us

      # have_content comes before host.shuld because capybara will wait for the former,
      # since they are ajaxy.
      page.should have_content("Forks?")
      page.should have_content("Public?")
      page.should have_content("Language")
      URI.parse(current_url).host.should == "circlehost"
      URI.parse(current_url).port.should == 3001
      URI.parse(current_url).path.should == join_path
      click_button "Add projects"


      URI.parse(current_url).path.should == join_path
      page.should have_content("We've got your code - we'd better get you an account")
      find_field('user_name').value.should == user.name
      find_field('user_email').value.should == ""
      fill_in "user_email", :with => user.email
      fill_in "user_password", :with => user.password
      click_button "Sign up"

      URI.parse(current_url).path.should == root_path
      page.should_not have_content("guest")
      page.should have_content(user.email)
      page.should have_content "Your builds should now be working"
      page.should have_content("circle-dummy-project")
    end

    it "if you stop before finishing, don't allow logins" do
      pending
    end

    it "works to sign in if you have no projects" do
      pending
    end

    it "when signing up, add their name to the db" do
      pending
    end
  end
end
