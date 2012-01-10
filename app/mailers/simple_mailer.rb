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
    @users = @project.users
    @emails = @users.map { |u| u.email }.find_all { |e| e.include? "@" }

    if Rails.env.production? && !@project.visible
      @emails = "founders@circleci.com"
    end

    subject = "[#{@project.github_project_name}] Test #{@build.build_num} #{@build.failed ? "failed" : "succeeded"}"

    if @build.failed
      @logs = @build.logs
      @failing_log = @logs.last
      @other_logs = @logs[0..-2]
      raise if @failing_log and @failing_log.success?
      mail(:to => @emails, :subject => subject, :template_name => "fail").deliver
    else
      mail(:to => @emails, :subject => subject, :template_name => "success").deliver
    end
  end

  def build_error_email(build_id, error)
#    @build = Build.find(build_id)
    mail(:to => "founders@circleci.com",
         :subject => "#{Rails.env}: build exception").deliver
  end

  def test(address="founders@circleci.com")
    mail(:to => address,
         :subject => "#{Rails.env}: test email").deliver
  end
end
