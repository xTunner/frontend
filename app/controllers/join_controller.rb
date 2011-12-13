class JoinController < ApplicationController


  # When the user is not yet signed up, we get their project information from
  # them first. We store all this in a "guest" user, and then at the end, we
  # sign the guest user in, and update the information with their real info. The
  # reason we go this way, instead of storing the information in a session, is
  # that it works just fine this way, and changing it is a hassle. In
  # particular, if we change it, we have to find a way for the front/back end to
  # pass information around, which is a bit of a refactoring away from working
  # well.
  def all
    @url = Github.authorization_url join_url
    @user = current_or_guest_user

    initialize_state
    @step, @substep = step_for_state
    do_state_action
  end

  def dynamic
    fetcher = session[:fetcher]
    if fetcher then
      ready = Backend.worker_done? fetcher
      if ready then
        @result = Backend.wait_for_worker fetcher
        session[:fetcher] = nil
      end
    end

    # TODO I used to move this along using the session[:next] thing

    if ready

      increment_state
      do_state_action

      step, substep = step_for_state
      body = render_to_string :partial => "body#{step}_#{substep}"
      explanation = render_to_string :partial => "explanation#{step}"
    end

    keep_polling = (not session[:stop])
    session[:stop] = false

    packet = {
      :step => step,
      :substep => substep,
      :ready => ready,
      :keep_polling => keep_polling,
      :body => body,
      :explanation => explanation,
    }.to_json

    render :json => packet
  end

  def add_projects
    projects = []
    params.each do |key, value|
      if value == "add_project"
        projects.push key
      end
    end

    # TECHNICAL_DEBT (minor for now): a better way to model github is to
    # constantly sync projects and users into special GH models, and look them
    # up directly.

    # Add all projects the user has access to.
    projects.each do |user_repo_pair|
      username, projectname = user_repo_pair.split "/"
      gh_url = Backend.blocking_worker "circle.backend.github-url/canonical-url", username, projectname

      project = Project.where(:vcs_url => gh_url).first

      allowed_urls = session[:allowed_urls] || []
      allowed = allowed_urls.any? { |u| u == gh_url }

      next if not allowed

      if not project
        project = Project.create! :name => projectname, :vcs_url => gh_url
      end

      project.users << current_or_guest_user
      project.save!

      Github.add_deploy_key current_or_guest_user, project, username, projectname
      Github.add_commit_hook username, projectname, current_or_guest_user
    end

    session[:allowed_urls] = nil

    increment_state
    do_state_action
    if current_or_guest_user.sign_in_count > 0
      redirect_to root_url
    else
      redirect_to join_url
    end
  end


  def increment_state
    session[:state] =
      case session[:state]
      when :start
        session[:stop] = true
        :start
      when :authorizing
        :fetching_projects
      when :fetching_projects
        session[:stop] = true
        :list_projects
      when :list_projects
        :signup
      when :signup
        session[:stop] = true
        :signup
      end
  end


  def do_state_action
    code = params[:code]
    access_token = current_or_guest_user.github_access_token

    # Start whatever job the state requires
    case session[:state]
    when :start
      # TODO: put this into the logs in a more structured way. But for now, we
      # have this so that we can compare it to people who follow through.
      logger.info "Started signup from #{params[:email]}"

      # Some stats
      current_or_guest_user.signup_channel = params["source"]
      current_or_guest_user.signup_referer = request.env["HTTP_REFERER"]

      session[:stop] = true


    when :authorizing
      session[:fetcher] = Github.fetch_access_token current_or_guest_user, code

    when :fetching_projects
      session[:fetcher] = Github.tentacles "repos/repos", current_or_guest_user
      Backend.fire_worker "circle.workers.github/add-user-details", current_or_guest_user.id.to_s

    when :list_projects
      session[:allowed_urls] = @result.map { |p| p[:html_url] }

    end


    # # TODO: start a worker which gets a list of builds
    # # TODO: in the background, check them out, infer them, and stream the build to the user.
    # # TODO: this means not waiting five minutes for the build to start!
  end


  def initialize_state
    code = params[:code]
    access_token = current_or_guest_user.github_access_token

    session[:state] =
      if current_or_guest_user.sign_in_count > 0
        :fetching_projects
      elsif code.nil? and access_token.nil?
        :start
      elsif code and access_token.nil?
        :authorizing
      elsif current_or_guest_user.projects.count > 0
        # They have a project but havent signed in yet, give them the form
        :signup
      else
        # authenticated, but no projects, show them the project form
        :fetching_projects
      end
  end

  def step_for_state
    case session[:state]
    when :start
      [1,1]
    when :authorizing
      [1,2]
    when :fetching_projects
      [2,1]
    when :list_projects
      [2,2]
    when :signup
      [3,1]
    end
  end



  # https://github.com/plataformatec/devise/wiki/How-To:-Create-a-guest-user
  # Obviously, we're going to be using guest users. Do not call
  # current_user anywhere in the controller until after we're certain
  # the user must be logged in
  def current_or_guest_user
    if current_user
      if session[:guest_user_id]
        session[:guest_user_id] = nil
      end
      current_user
    else
      guest_user
    end
  end

  def guest_user
    if session[:guest_user_id]
      begin
        return User.find session[:guest_user_id]
      rescue Exception => e
      end
    end
    u = create_guest_user
    session[:guest_user_id] = u.id
    u
  end

  def create_guest_user
    id = BSON::ObjectId.new()
    u = User.create(:_id => id, :email => "guest_#{id.to_s}")
    u.save(:validate => false)
    u
  end

end
