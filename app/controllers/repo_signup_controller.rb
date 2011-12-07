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

  def all
    @url = Github.authorization_url add_repo_url

    code = params[:code]
    access_token = current_user.github_access_token

    state = starting_state(code, access_token)
    @step, @substep = step_for_state(state)
    start_job(state, params)
    session[:state] = state
  end

  def start_job(state, params)
    # TODO: refactor this duplication
    code = params[:code]
    access_token = current_user.github_access_token

    # Start whatever job the state requires
    case state
    when :start
      # TODO: put this into the logs in a more structured way. But for now, we
      # have this so that we can compare it to people who follow through.
      logger.info "Started signup from #{params[:email]}"

      # Some stats
      current_user.signup_channel = params["source"]
      current_user.signup_referer = request.env["HTTP_REFERER"]
      current_user.save!

    when :authorizing
      fetcher = Github.fetch_access_token(current_user, code)

    when :authorized
      session[:next] = true

    when :fetching_projects
      session[:next] = true
      # fetcher = Github.tentacles "repos/repos", current_user

    when :list_projects
      session[:next] = true
      # TODO

    when :adding_keys
      session[:next] = true
      # TODO

    when :done
      #TODO
    end

    session[:fetcher] = fetcher

    # # TODO: start a worker which gets a list of builds
    # # TODO: in the background, check them out, infer them, and stream the build to the user.
    # # TODO: this means not waiting five minutes for the build to start!
  end


  def starting_state(code, access_token)
    if code.nil? and access_token.nil?
      :start
    elsif code and access_token.nil? then
      :authorizing
    else
      :list_projects
    end
  end

  def next_state(state)
    case state
    when :start
       # They need to click to github and come back, we can't do this for them.
      :start
    when :authorizing
      :authorized
    when :authorized
      :fetching_projects
    when :fetching_projects
      :list_projects
    when :list_projects
      :adding_keys
    when :adding_keys
      :done
    when :done
      :done # saturate
    end
  end


  def step_for_state(state)
    case state
    when :start
      [1,1]
    when :authorizing
      [1,2]
    when :authorized
      [1,3]
    when :fetching_projects
      [2,1]
    when :list_projects
      [2,2]
    when :adding_keys
      [2,3]
    when :done
      [3,1]
    end
  end


  def dynamic
    state = session[:state]
    fetcher = session[:fetcher]
    if fetcher then
      ready = Backend.worker_done? fetcher
      if ready then
        result = Backend.wait_for_worker fetcher
        session[:fetcher] = nil
      end
    end

    # Just move through the states
    if session[:next] then
      ready = true
      session[:next] = false
    end

    if ready then
      state = next_state(state)
      start_job(state, params)
      session[:state] = state
      step, substep = step_for_state(next_state(state))
      body = render_to_string :partial => "body#{step}_#{substep}"
      explanation = render_to_string :partial => "explanation#{step}"
    end

    result = {
      :step => step,
      :ready => ready,
      :state => state,
      :body => body,
      :explanation => explanation,
    }.to_json
    puts result
    render :json => result
  end
end
