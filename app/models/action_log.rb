class ActionLog
  include Mongoid::Document

  field :exit_code, :type => Integer, :default => nil
  field :name, :type => String
  field :start_time, :type => Time
  field :end_time, :type => Time, :default => nil
  field :out

  # can't call this build, that's a reserved work in Mongoid
  belongs_to :thebuild, :class_name => "Build"

  def success?
    exit_code == 0 || exit_code == nil
  end

  def status
    if end_time == nil
      :running
    elsif exit_code == 0 || exit_code == nil
      :success
    else
      :error
    end
  end
end
