require 'spec_helper'

describe SimpleMailer do

  it "should send a success email" do
    # TECHNICAL_DEBT: these should be in the factory
    b = Factory.create(:successful_build)
    l = Factory(:successful_log)
    b.action_logs.push l
    b.save

    SimpleMailer.build_email(b.id)
    ActionMailer::Base.deliveries.length.should == 1
    mail = ActionMailer::Base.deliveries.last

    mail.subject.should == "[circleci/circle-dummy-project] Test 1 succeeded"
    mail.to.should == ["user@test.com"]
    mail.from.should == ["builds@circleci.com"]
    mail.body.parts.length.should == 2

    html = mail.body.parts.find {|p| p.content_type.match /html/}.body.raw_source
    text = mail.body.parts.find {|p| p.content_type.match /plain/}.body.raw_source

    link = "http://circlehost:3000/gh/circleci/circle-dummy-project/1"
    html.should have_tag "a", :text => "Read the full build report", :href => link
    text.should include "Read the full build report: #{link}"

    html.should include "Commit abcdef123456789 has passed all its tests!"
    text.should include "Commit abcdef123456789 has passed all its tests!"

    link = "http://circlehost:3000/gh/circleci/circle-dummy-project/1"
    html.should have_tag "a", :text => "Read the full build report", :href => link
    text.should include ": #{link}"

    html.should include "These commands were run, and were all successful:"
    html.should include "ls -l"
    # check no more commands
    text.should include "These commands were run, and were all successful:\n  ls -l\n\n"
  end

  it "should send a fail mail" do
    # TECHNICAL_DEBT: these should be in the factory
    b = Factory.create(:failing_build)
    l = Factory(:failing_log)
    b.action_logs.push l
    b.save

    SimpleMailer.build_email(b.id)
    ActionMailer::Base.deliveries.length.should == 1
    mail = ActionMailer::Base.deliveries.last

    mail.subject.should == "[circleci/circle-dummy-project] Test 1 failed"
    mail.to.should == ["user@test.com"]
    mail.from.should == ["builds@circleci.com"]
    mail.body.parts.length.should == 2

    html = mail.body.parts.find {|p| p.content_type.match /html/}.body.raw_source
    text = mail.body.parts.find {|p| p.content_type.match /plain/}.body.raw_source

    link = "http://circlehost:3000/gh/circleci/circle-dummy-project/1"
    html.should have_tag "a", :text => "Read the full build report", :href => link
    text.should include ": #{link}"

    html.should include "has failed its tests!"
    text.should include "has failed its tests!"

    html.should include "The rest of your commands were successful:"
    # check no more commands
    text.should include "The rest of your commands were successful:\n\n"

    html.should include "Output"
    html.should include "Exit code: 127"
    text.should include "Output:\n\nExit code: 127"
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

  it "should send a first email" do
#    "this is your first build"
    pending
  end

  it "should send a 'just broke it' mail" do
    #    "hi john you broke the build"
    pending
  end
end
