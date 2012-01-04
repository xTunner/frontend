class SimpleMailer < ActionMailer::Base
  default :from => "Circle Builds <build-fairy@circleci.com>"
  include ApplicationHelper
  helper :application

  # Sends an email for a build:
  # - work out who should be emailed based on their preferences
  # - decide what type of email (depends on the branch, previous success)
  # - email them
  def build_email(build_id)

    @build = Build.find(build_id)
    @users = @build.the_project.users
    #    @emails = @users.map { |u| u.email }.find_all { |e| e.include? "@" }
    @emails = "founders@circleci.com"
    @project = @build.the_project

    if @build.failed?
      @logs = @build.logs
      @failing_log = @logs.last
      @other_logs = @logs[0..-1]
      raise if @failing_log.success?
      mail(:to => @emails, :subject => "[#{@project.github_project_name}] Test #{@build.build_num} failed", :template_name => "fail").deliver
    else
      mail(:to => @emails, :subject => "[Circle] Tests succeeded (#{@project.github_project_name} #{@build.build_num})", :template_name => "success").deliver
    end

    email.deliver
  end

  def build_error_email(build_id, error)
#    @build = Build.find(build_id)
    mail(:to => "founders@circleci.com",
         :subject => "#{Rails.env}: build exception").deliver
  end
end
