class SimpleMailer < ActionMailer::Base
  default :from => "Circle Builds <builds@circleci.com>"
  include ApplicationHelper
  helper :application
  layout 'mailer'

  # Notifications via emails:
  # We send notifications based on a user's preferences, and based on the status
  # of the previous build.

  def post_build_email_hook(build)
    # common to all emails
    @build = build
    @project = @build.project
    @last_build = @build.parent_build

    @logs = @build.logs
    @last_log = @logs.last
    @successful_logs = @logs.find_all { |l| l.success? }

    users = @project.users.find_all { |u| !u.is_guest? }

    # User specific emails, like "welcome"
    users.each { |u| self.maybe_send_first_email u }

    # Emails for the build, like "pass" and "fail"
    send_build_email users
  end

  def send_build_email(users)
    status = @build.status
    subject = @build.as_email_subject
    cc = []
    cc << "engineering@circleci.com" if status == :infrastructure_fail
    bcc = ["founders@circleci.com"]

    to = users.find_all { |u| u.wants_build_email?(@build) }.map {|u| u.email }
    return if to == [] && cc == []

    if status == :infrastructure_fail
      to = ["founders@circleci.com"]
    end

    # Just send to the user if it was triggered
    if @build.why == "trigger" || @build.why == "edit"
      to = [@build.user.email]
    end

    mail(:to => to, :cc => cc, :bcc => bcc,
         :subject => subject,
         :template_name => status.to_s).deliver
  end


  # send the first email to user, if necessary
  def maybe_send_first_email(user)
    return # disable until this is finished
    if not user.sent_first_build_email and user.build_in_every_project?
      send_first_email(user)
    end
  end

  def send_first_email(user)
    @projects = user.projects

    mail(:to => user.email,
         :subject => "All your projects have built",
         :template_name => "first_build").deliver
    user.sent_first_build_email = true
    user.save!
  end

  def test(address="founders@circleci.com")
    mail(:to => address,
         :subject => "#{Rails.env}: test email").deliver
  end
end
