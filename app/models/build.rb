## The canonical Build model lives in the clojure code. This definition exists to make Rail's querying easier
class Build
  include Mongoid::Document

  field :vcs_url
  field :vcs_revision

  field :failed, :type => Boolean, :default => nil
  field :start_time, :type => Time, :default => nil
  field :stop_time, :type => Time, :default => nil
  field :build_num, :type => Integer

  belongs_to :project

  def the_project
    # TECHNICAL_DEBT: the clojure and ruby models are not synced, so we don't have access to the build model directly
    Project.where(:vcs_url => vcs_url).first
  end

  def logs
    ActionLog.where("_build-ref" => id).all
  end


  def status
    if self.stop_time
      if self.failed == true
        :fail
      else
        :success
      end
    elsif (self.start_time + 24.hours) < Time.now
      :killed
    else
      :running
    end
  end
end
