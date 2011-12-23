# The build specifications. This produces a build object that the backend uses to actually run a build.
class Spec
  include Mongoid::Document
  include Mongoid::Versioning
  include Mongoid::Timestamps

  # These are really arrays of fields, but it's easier to use \n as a delimiter for now.
  field :setup, :type => String, default => ""
  field :dependencies, :type => String, default => ""
  field :dependencies, :type => String, default => ""
  field :test, :type => String, default => ""

end
