class SimpleMailer < ActionMailer::Base
  default :from => "notifications@circleci.com"

  # Sends an email for a build:
  # - work out who should be emailed based on their preferences
  # - decide what type of email (depends on the branch, previous success)
  # - email them
  def build_email(build_id)

    mail :to => "paul@circleci.com", :subject => "test"

    @build = Build.find(build_id)

    if @build.failed?
      render "fail"
    else
      render "success"
    end

  end

  def test_email
    @build = Build.all.last
    mail(:to => "paul@circleci.com", :subject => "a test email").deliver
  end
end
