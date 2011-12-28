require 'spec_helper'

describe JoinController do

  it "should track the source" do
    pending
  end

  describe "log in" do
    login_user
    it "sign_up_at should be very very recent" do
      subject.current_user.sign_in_count.should == 0
      subject.current_user.signup_at.should > (Time.now - 1)
    end
  end
end
