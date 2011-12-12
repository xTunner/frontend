class RegistrationsController < Devise::RegistrationsController
  prepend_before_filter :sign_guest_user_in

  # As soon as the user hits the add-project page, we immediately set them up
  # with an account, but that account has no password. At the end of the form,
  # we ask them to update the password. Since they already have an account (the
  # one we just set up), they must "update" their account, which Devise doesn't
  # allow since you can only do that if you're signed in. So sign the user in
  # first.
  def sign_guest_user_in
    user = User.find session[:guest_user_id]
    sign_in :user, user
  end
end
