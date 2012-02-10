class ActionLog
  include Mongoid::Document

  field :command, :type => String, :default => -> { name }
  field :out
  field :exit_code, :type => Integer, :default => nil
  field :start_time, :type => Time
  field :end_time, :type => Time, :default => nil
  field :timedout, :type => Boolean, :default => nil
  field :infrastructure_fail, :type => Boolean, :default => nil

  field :name, :type => String

  # TECHNICAL_DEBT: we need to migrate old types and sources
  field :type, :type => String, :default => "test"
  field :source, :type => String, :default => nil # inferred, yaml or spec


  # can't call this build, that's a reserved work in Mongoid
  # TECHNICAL_DEBT: this needs a migration to be populated
  belongs_to :thebuild, :class_name => "Build"

  def success?
    status == :success
  end

  def status
    if end_time == nil
      :running
    elsif timedout
      :timedout
    elsif exit_code == 0 || exit_code == nil
      :success
    else
      :failed
    end
  end

  def output
    return "" if out.nil?
    out.map{ |o| o["message"]}.join ""
  end
end
