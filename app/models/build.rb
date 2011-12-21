## The canonical Build model lives in the clojure code. This definition exists to make Rail's querying easier
class Build
  include Mongoid::Document

  field :vcs_url

  field :failed?, :type => Boolean
  field :start_time, :type => Time, :default => nil
  field :stop_time, :type => Time, :default => nil

  belongs_to :project

  def status
    if self.failed? == true
      :fail
    elsif self.failed? == false
      :success
    elsif (self.start_time + 24.hours) < Time.now
      :killed
    else
      :running
    end
  end
end
