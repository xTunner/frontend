require 'spec_helper'

describe AdminController do

  describe "normal user" do
    login_user

    it "shouldnt give access to non admins" do
      lambda {
        get 'show'
      }.should raise_error(CanCan::AccessDenied)
    end

  end

  describe "admin logged in" do
    login_admin_user

    it "should give access to admins" do
      get 'show'
    end
  end
end
