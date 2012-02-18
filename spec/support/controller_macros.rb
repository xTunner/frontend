module ControllerMacros
  def login_user
    before(:each) do
      @request.env["devise.mapping"] = Devise.mappings[:user]
      user = User.create! :email => "user@test.com"
      sign_in user
    end
  end

  def login_admin_user
    before(:each) do
      @request.env["devise.mapping"] = Devise.mappings[:user]
      user = User.create! :email => "admin@test.com"
      user.admin = true
      user.save!

      sign_in user
    end
  end

end
