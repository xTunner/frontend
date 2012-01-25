require 'spec_helper'

describe SimpleMailer do

  let(:vcs_url) { "https://github.com/a/b" }
  let(:vcs_revision) { "abcdef0123456789" }

  let(:author) { User.create(:name => "Bob", :email => "author@test.com") } # default emails prefs

  let(:lover) { User.create(:name => "Bob",
                            :email => "lover@test.com",
                            :email_preferences => {
                              "on_fail" => ["all"],
                              "on_success" => ["all"],
                            })}

  let(:hater) { User.create(:name => "Bob", :email => "hater@test.com", :email_preferences => {}) }
  let(:users) { [author, hater, lover] }

  let!(:project) { p = Project.create; p.vcs_url=vcs_url; p.users=users; p.save!; p }

  let(:out1) { { :type => "out", :time => nil, :message => "a message" } }
  let(:successful_log) { ActionLog.create(:type => "test", :name => "ls -l", :exit_code => 0, :out => [out1]) }
  let(:setup_log) { ActionLog.create(:type => "setup", :name => "ls -l", :exit_code => 0, :out => [out1]) }
  let(:failing_log) { ActionLog.create(:type => "test", :name => "ls -l", :exit_code => 1, :out => []) }

  let(:std_attrs) do
    {
      :vcs_url => vcs_url,
      :start_time => Time.now - 10.minutes,
      :stop_time => Time.now,
      :vcs_revision => vcs_revision,
      :subject => "That's right, I wrote some code",
      :committer_email => author.email,
      :build_num => 1
    }
  end

  let(:successful_build) do
    Build.create(std_attrs.merge(:action_logs => [successful_log], :failed => false))
  end

  let(:failing_build) do
    Build.create(std_attrs.merge(:action_logs => [failing_log], :failed => true))
  end

  let(:infra_build) do
    Build.create(std_attrs.merge(:action_logs => [failing_log], :failed => true, :infrastructure_fail => true))
  end

  let(:timedout_build) do
    Build.create(std_attrs.merge(:action_logs => [failing_log], :failed => true, :timed_out => true))
  end

  let(:no_tests_build) do
    Build.create(std_attrs.merge(:action_logs => [setup_log], :failed => false))
  end

  #  let(:fixed_build) { Build.create(:vcs_url => vcs_url, :parent_build => [failing_build]

  shared_examples "an email" do |build_sym, subject_regexes, body_regexes|

    let(:build) { send(build_sym) }
    let!(:mails) do
      SimpleMailer.post_build_email_hook(build.id);
      ActionMailer::Base.deliveries
    end

    let(:mail) { mails.first }
    let(:html) { mail.body.parts.find {|p| p.content_type.match /html/}.body.raw_source }
    let(:text) { mail.body.parts.find {|p| p.content_type.match /plain/}.body.raw_source }
    let(:build_report) { "http://circlehost:3000/gh/" + build.project.github_project_name + '/' + build.build_num.to_s }

    it "should send one email" do
      ActionMailer::Base.deliveries.length.should == 1
    end

    it "should be sent to the right users" do
      mail.to.should include lover.email
      mail.to.should include author.email
      mail.to.should_not include hater.email
    end

    subject_regexes.each do |r|
      it "should check the subject's contents" do
        mail.subject.should match r
      end
    end

    body_regexes.each do |r|
      it "should check the subject's body" do
        html.should match r
        text.should match r
      end
    end

    it "should have the right subject" do
      mail.subject.should include ": a/b#1 - author: That's right, I wrote some code"
      mail.subject.should
    end

    it "should be from the right person" do
      mail.from.should == ["builds@circleci.com"]
    end

    it "should have text and multipart" do
      mail.body.parts.length.should == 2
    end

    it "should have a link to the build report" do
      html.should have_tag("a", :text => "Read the full build report", :href => build_report)
      text.should include "Read the full build report: #{build_report}"
    end

    it "should list the revision number" do
      html.should include "Commit abcdef0123456789"
    end

    it "should list the commands" do
      build.logs.each do |l|
        html.should include l.command
        text.should include l.command
      end
    end
  end


  describe "the contents and recipients of the emails" do

    describe "success email" do
      it_should_behave_like("an email",
                            :successful_build,
                            [/^Success:/],
                            [/has passed all its tests!/,
                             /These commands were run, and were all successful:/]) do
      end
    end

    describe "failing email" do
      it_should_behave_like("an email",
                            :failing_build,
                            [/^Fail:/],
                            [/has failed its tests!/,
                             /The rest of your commands were successful:/,
                             /Output: /,
                             /Exit code: 127/]) do
      end
    end

    describe "no tests email" do
      it_should_behave_like("an email",
                            :no_tests_build,
                            [/^No tests:/],
                            [/did not run any tests!/,
                             /The rest of your commands were successful:/]) do
      end
    end

    describe "infrastructure fail email" do
      it_should_behave_like("an email",
                            :infra_build,
                            [/^Infrastructure fail:/],
                            [/did not complete due to a problem with Circle's infrastructure. We're looking into it./]) do
        it "should CC us" do
          mail.cc.should == ["engineering@circleci.com"]
        end
      end
    end

    describe "timedout email" do
      it_should_behave_like("an email",
                            :timedout_build,
                            [/^Timed out:/],
                            [/timed out and could not be confirmed./]) do
      end
    end
    it "should send a 'fixed' email to the author" do
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
