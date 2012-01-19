class ActionLog
  include Mongoid::Document

  field :command, :type => String, :default => nil
  field :out
  field :exit_code, :type => Integer, :default => nil
  field :start_time, :type => Time
  field :end_time, :type => Time, :default => nil

  field :name, :type => String
  field :type, :type => String, :default => nil # setup, tests, etc - same names as the field in the spec
  field :source, :type => String, :default => nil # inferred, yaml or spec


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
