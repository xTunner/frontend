## The canonical Build model lives in the clojure code. This definition exists to make Rail's querying easier
class Build
  include Mongoid::Document

  field :vcs_url
  field :vcs_revision

  field :start_time, :type => Time, :default => nil
  field :stop_time, :type => Time, :default => nil
  field :build_num, :type => Integer
  field :failed, :type => Boolean, :default => nil

#  belongs_to :project

  has_many :action_logs, :inverse_of => :thebuild

  def the_project
    # TECHNICAL_DEBT: the clojure and ruby models are not synced, so we don't
    # have access to the build model directly
    Project.where(:vcs_url => vcs_url).first
  end

  def logs
    if action_logs.length > 0
      action_logs
    else
      ActionLog.where("_build-ref" => id).all
    end
  end


  def status
    if failed
      :fail
    elsif stop_time
      :success
    elsif (start_time + 24.hours) < Time.now
      :killed
    else
      :running
    end
  end
end
