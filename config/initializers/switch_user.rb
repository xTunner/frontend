SwitchUser.setup do |config|
  config.available_users = { :user => lambda { User.where(:email => /@/) } }

  config.view_guard = lambda do |current_user, request|
    current_user && current_user.admin?
  end

  config.controller_guard = lambda do |current_user, request|
    current_user && current_user.admin?
  end

end
