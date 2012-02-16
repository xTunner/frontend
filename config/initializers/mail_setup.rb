ActionMailer::Base.smtp_settings = {
  :address => "smtp.mailgun.org",
  :user_name => "postmaster@circleci.com",
  :password => ENV["MAILGUN_SMTP_PASSWORD"],
  :port => 587,
  :domain => "circleci.com",
  :authentication => :plain,
  :enable_starttls_auto => true,
}

## Google Apps Settings, for backup if necessary.

# ActionMailer::Base.smtp_settings = {
#   :address => "smtp.gmail.com",
#   :port => 465,
#   :domain => "circleci.com",
#   :user_name => "builds@circleci.com",
#   :password => ENV['GMAIL_SMTP_PASSWORD'],
#   :enable_starttls_auto => true,
#   :authentication => :plain
# }
