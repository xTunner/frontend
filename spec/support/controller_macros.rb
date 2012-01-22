module ControllerMacros
  def login_user
    before(:each) do
      @request.env["devise.mapping"] = Devise.mappings[:user]
      # If this is a problem, lookup the user using FactoryGirl.find(:user)
      user = FactoryGirl.create(:user)
      sign_in user
    end
  end

  def login_admin_user
    before(:each) do
      @request.env["devise.mapping"] = Devise.mappings[:user]
      # If this is a problem, lookup the user using FactoryGirl.find(:user)
      user = FactoryGirl.create(:admin_user)
      sign_in user
    end
  end

end
