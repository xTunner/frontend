module ControllerMacros
  def login_user
    before(:each) do
      @request.env["devise.mapping"] = Devise.mappings[:user]
      # If this is a problem, lookup the user using FactoryGirl.find(:user)
      user = Factory(:user)
      sign_in user
    end
  end
end
