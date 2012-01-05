if defined?(MailSafe::Config)
  MailSafe::Config.internal_address_definition = lambda { |addr| false }
  MailSafe::Config.replacement_address = 'founders@circleci.com' if (MailSafe::Config.developer_email_address == "")
end
