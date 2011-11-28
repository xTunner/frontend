class Job
  include Mongoid::Document

  belongs_to :project
end
