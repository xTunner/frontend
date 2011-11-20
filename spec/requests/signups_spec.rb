require 'spec_helper'

describe "Signup" do

  describe "success" do

    it "should make a new user" do
      thanks = "Thanks! We'll be in touch soon"

      visit root_path
      fill_in "email",        :with => "asd@asd.com"
      check "contact"
      click_button "Get Notified"
      page.should have_content(thanks)

      # visit a second time and we should have the form back to normal
      visit root_path
      page.should_not have_content(thanks)
    end
  end
end
