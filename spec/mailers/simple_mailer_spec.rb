require 'spec_helper'

describe SimpleMailer do

  def setup(project, user, build, log)
    build = FactoryGirl.create(build)
    project = FactoryGirl.create(project)
    user = FactoryGirl.create(user)
    log = FactoryGirl.create(log)
    raise if build.vcs_url != project.vcs_url
    project.users << user
    build.action_logs << log
    project.save!
    build.save!
    user.save!
    log.save!
    SimpleMailer.post_build_email_hook(build.id)
    [project, user, build, log]
  end

  describe "send mails to the author with the right contents" do

    it "should send a 'success' email" do
      p, u, b, l = setup(:project, :email_lover, :successful_build, :successful_log)

      ActionMailer::Base.deliveries.length.should == 1
      mail = ActionMailer::Base.deliveries.last

      mail.subject.should == "Success: circleci/circle-dummy-project#1 - user: That's right, I wrote some code"
      mail.to.should == [u.email]
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

    it "should send a fail mail to the author" do
      p, u, b, l = setup(:project, :email_lover, :failing_build, :failing_log)

      ActionMailer::Base.deliveries.length.should == 1
      mail = ActionMailer::Base.deliveries.last

      mail.subject.should == "Fail: circleci/circle-dummy-project#1 - user: That's right, I wrote some code"
      mail.to.should == [u.email]
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

    it "should send a 'fixed' email to the author" do
      pending
    end

    it "should send a 'no tests' email to the author" do
      pending
    end

    it "should send an 'infrastructure fail' email to the author" do
      pending
    end

    it "should send an 'timedout' email to the author" do
      pending
    end
  end

  describe "send emails to people who follow that branch" do
    it "should send a 'success' email to an 'all' subscriber" do
      pending
    end

    it "should send a 'fail' email to an 'all' subscriber" do
      pending
    end

    it "should send a 'fixed' email to the author" do
      pending
    end

    it "should send a 'no tests' email to the author" do
      pending
    end

    it "should send an 'infrastructure fail' email to the author" do
      pending
    end

    it "should send an 'timedout' email to the author" do
      pending
    end
  end

  describe "shouldn't send mail to people who don't want them" do
    it "should not send a success email" do
      pending
    end

    it "should not send a failed email" do
      pending
    end

    it "should not send a fixed email" do
      pending
    end

    it "should not send a 'no tests' email" do
      pending
    end

    it "should not send an 'infrastructure fail'" do
      pending
    end

    it "should not send an 'timedout' email" do
      pending
    end
  end

  it "should send a first email" do
    pending # check the contents
  end

  it "should check the output doesn't have extra lines between it" do
    pending
  end
end
