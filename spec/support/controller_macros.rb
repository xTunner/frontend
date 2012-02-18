module ControllerMacros
  # NOTE: before calling login_user or login_admin_user, you need to add the user to the DB using use_clojure_factories
  def login_user
    before(:each) do
      @request.env["devise.mapping"] = Devise.mappings[:user]
      user = User.where(:email => "user@test.com").first
      sign_in user
    end
  end

  def login_admin_user
    before(:each) do
      @request.env["devise.mapping"] = Devise.mappings[:user]
      user = User.where(:email => "admin@test.com").first
      sign_in user
    end
  end

end
