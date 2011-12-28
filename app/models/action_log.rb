class ActionLog
  include Mongoid::Document

  field :exit_code, :type => Integer, :default => nil

  def success?
    exit_code == 0 || exit_code == nil
  end
end
