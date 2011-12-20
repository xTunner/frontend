## The canonical Build model lives in the clojure code. This definition exists to make Rail's querying easier
class Build
  include Mongoid::Document

  field :vcs_url
  field :start_date

  belongs_to :project
end
