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
      page.should have_content("Thanks for joining!")
    end
  end
end
