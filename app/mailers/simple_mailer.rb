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

    to = users.find_all { |u| u.wants_build_email?(@build) }.map {|u| u.email }
    return if to == [] && cc == []

    # TODO: stop doing this "visible" hack
    if Rails.env.production? && !@project.visible
      to = ["founders@circleci.com"]
    end

    mail(:to => to, :cc => cc, :subject => subject, :template_name => status.to_s).deliver
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

  def build_error_email(build_id, error)
    @build = Build.find(build_id)
    @error = error.to_s
    mail(:to => "founders@circleci.com",
         :subject => "#{Rails.env}: build exception").deliver
  end

  def test(address="founders@circleci.com")
    mail(:to => address,
         :subject => "#{Rails.env}: test email").deliver
  end
end
