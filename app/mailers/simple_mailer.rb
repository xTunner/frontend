class SimpleMailer < ActionMailer::Base
  default :from => "notifications@circleci.com"
  include ApplicationHelper
  helper :application

  # Sends an email for a build:
  # - work out who should be emailed based on their preferences
  # - decide what type of email (depends on the branch, previous success)
  # - email them
  def build_email(build_id)
    return unless Rails.env.development?

    @build = Build.find(build_id)
    @users = @build.the_project.users
    @emails = @users.map { |u| u.email }.filter { |e| e.include? "@" }

    if @build.failed?
      @logs = @build.logs
      @failing_log = @logs.last
      @other_logs = @logs[0..-1]
      raise unless @failing_log.success?
      mail(:to => @emails, :subject => "Your build failed!").deliver
      render "fail"
    else
      mail(:to => @emails, :subject => "Your build succeeded").deliver
      render "success"
    end
  end

  def test_email
    @build = Build.all.last
    build_email(@build.id)
  end
end
