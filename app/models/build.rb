## The canonical Build model lives in the clojure code. This definition exists to make Rail's querying easier
class Build
  include Mongoid::Document

  # Raw fields
  field :vcs_url
  field :vcs_revision
  field :build_num, :type => Integer
  field :start_time, :type => Time, :default => nil
  field :stop_time, :type => Time, :default => nil # can be empty
  field :infrastructure_fail, :type => Boolean, :default => false
  field :timedout, :type => Boolean, :default => false
  has_many :action_logs, :inverse_of => :thebuild
#  belongs_to :project


  # Refined fields (opposite of 'raw")
  # These fields should be moved into the refined map
  field :failed, :type => Boolean, :default => nil
  field :parents, :type => Array, :default => nil
  field :subject, :type => String, :default => nil
  field :body, :type => String, :default => nil
  field :branch, :type => String, :default => nil

  field :committer_name, :type => String, :default => nil
  field :committer_email, :type => String, :default => nil
  field :committer_date, :type => Time, :default => nil
  field :author_email, :type => String, :default => nil
  field :author_name, :type => String, :default => nil
  field :author_date, :type => Time, :default => nil


  def the_project
    # TECHNICAL_DEBT: the clojure and ruby models are not synced, so we don't
    # have access to the build model directly
    Project.where(:vcs_url => vcs_url).first
  end

  def project
    the_project
  end

  def self.start(url)
    Build.create! :start_time => Time.now, :vcs_url => url
  end

  def committer_handle
    return nil if committer_email.nil?

    committer_email.split("@")[0]
  end

  def logs
    if action_logs.length > 0
      action_logs
    else
      ActionLog.where("_build-ref" => id).all
    end
  end

  # The last thing we built that is a reasonable parent for the build.
  def parent_build
    return nil
  end

  def just_started_passing?
    return parent_build && parent_build.failed && !failed
  end

  def started
    status != :starting
  end

  def test_logs
    logs.find_all { |l| l.type == "test" }
  end

  def has_test_logs?
    test_logs.length > 0
  end

  def status # Followed by '#E' if it's a valid email state
    if infrastructure_fail
      :infrastructure_fail #E
    elsif timedout
      # TODO: can :killed be merged into this
      :timedout #E
    elsif failed
      :failed #E
    elsif stop_time
      if !has_test_logs?
        :no_tests #E
      elsif just_started_passing?
        :fixed #E
      else
        :success #E
      end
    elsif (start_time + 3.hours) < Time.now
      :killed # just for display, not a real state
    elsif build_num != nil
      :running
    else
      :starting
    end
  end

  def status_in_words
    case status
    when :failed
      "has failed"
    when :no_tests
      "has no tests"
    when :fixed
      "fixed the build"
    when :success
      "passed it's tests"
    when :killed
      "had to be killed"
    when :running
      "is running"
    when :starting
      "is starting"
    when :infrastructure_fail
      "could not be run due to infrastructural problems"
    when :timedout
      "timed out and had to be killed"
    else
      raise "invalid option"
    end
  end

  def status_as_title
    case status
    when :failed
      "Failed"
    when :no_tests
      "No tests"
    when :fixed
      "Fixed"
    when :success
      "Success"
    when :killed
      "Killed"
    when :running
      "Running"
    when :starting
      "Starting"
    when :infrastructure_fail
      "Circle bug"
    when :timedout
      "Timed out"
    else
      raise "invalid option"
    end
  end

  def branch_in_words
    if branch.nil?
      "unknown"
    else
      branch
    end
  end

  def as_email_subject
    p = the_project
    "#{status_as_title}: #{project.github_project_name}##{build_num}" +
      " - " +
      "#{committer_handle}: #{subject[0..50]}"
  end

  def absolute_url
    Rails.application.routes.default_url_options = ActionMailer::Base.default_url_options
    Rails.application.routes.url_helpers.build_url self.project, self.build_num, :only_path => false
  end
end
