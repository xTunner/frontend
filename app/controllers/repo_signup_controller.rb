class RepoSignupController < ApplicationController
  before_filter :authenticate_user!

  # The is the page where users give us access to their repos. There are several ways to get here:
  #
  # - We send the user an invite, using devise. At this point, they are a real user, and the token
  #   handles the authentication. We'll just solve this case for now. We get their email from the
  #   Signup object.
  #
  # - We get a user from Exceptional. They have a token and an email address (slightly obscured). We
  #   store the token, and manually verify with Ben that we can charge them. We set them up as a
  #   normal user.
  #   TODO: this requires them to have a password with us!! Not completely friendly.
  #
  # - A user signs up on the homepage. We likely won't handle that here.
  def new

    # TODO: put this into the logs in a more structured way. But for now, we have this so that we
    # can compare it to people who follow through.
    logger.info "Started signup from #{params[:email]}"

    redirect = hooks_github_callback_url
    @url = Github.authorization_url redirect
    @project = Project.new

    # How did they get here?
    current_user.signup_channel = params["source"]
#    current_user.signup_referer = params["http-referer"]
    current_user.save!
  end

  def github_reply
    code = params[:code]
    if code
      @fetcher = Github.fetch_access_token(current_user, code)
    end
    # TODO: start a worker which gets a list of builds
  end

  def show
    # fetch the list of repos
    # TODO: in the background, check them out, infer them, and stream the build to the user.
    # TODO: this means not waiting five minutes for the build to start!
  end

  def repo_list
  end
end
