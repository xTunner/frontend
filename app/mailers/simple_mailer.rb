class SimpleMailer < ActionMailer::Base
  default :from => "Circle Builds <builds@circleci.com>"
  include ApplicationHelper
  helper :application

  # Sends an email for a build:
  # - work out who should be emailed based on their preferences
  # - decide what type of email (depends on the branch, previous success)
  # - email them
  def build_email(build_id)

    @build = Build.find(build_id)
    @project = @build.the_project
    @users = @project.users.find_all { |u| !u.is_guest? }
    @emails = @users.map { |u| u.email }

    # Don't fail when clojure tests have no users
    if Rails.env.test? && @emails == []
      @emails = "blackhole@circleci.com"
    end

    if Rails.env.production? && !@project.visible
      @emails = "founders@circleci.com"
    end

    subject = "[#{@project.github_project_name}] Test #{@build.build_num} #{@build.failed ? "failed" : "succeeded"}"

    if @emails.length > 0
      if @build.failed
        @logs = @build.logs
        @failing_log = @logs.last
        @other_logs = @logs[0..-2]
        if @failing_log and @failing_log.success?
          logger.error "Failing log didn't fail: #{@failing_log.to_s}"
        end
        mail(:to => @emails, :subject => subject, :template_name => "fail").deliver
      else
        mail(:to => @emails, :subject => subject, :template_name => "success").deliver
      end
    end
  end

  # send the first email to user, if necessary
  def maybe_send_first_email (user)
    if not user.send_first_build_email and user.build_in_every_project?
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
