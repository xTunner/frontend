class Project
  include Mongoid::Document
  field :name, :type => String
  field :vcs_url, :type => String
end
