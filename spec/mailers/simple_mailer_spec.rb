require 'spec_helper'

describe SimpleMailer do

  it "should send an email" do
    b = Factory(:build)
    SimpleMailer.build_email(b.id)
    ActionMailer::Base.deliveries.length.should == 1
  end

  it "should send an error email" do
    b = Factory(:build)
    SimpleMailer.build_error_email(b.id, "a test error")
    ActionMailer::Base.deliveries.length.should == 1
  end


  it "should send a 'good transition' email" do
    # old_build = failing_build
    # new_build = passing_build
    # main.should be_positive
    # mail.should be_sent_to_all_recipients_of_previous_email
    pending
  end

  it "should send a success mail" do
    pending
  end

  it "should send a first email" do
#    "this is your first build"
    pending
  end

  it "should send a 'just broke it' mail" do
    #    "hi john you broke the build"
    pending
  end
end
