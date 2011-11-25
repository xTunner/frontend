class Project
  include Mongoid::Document
  field :name, :type => String
  field :vcs_type, :type => String
  field :vcs_url, :type => String
  field :aws_credentials, :type => String
  field :ssh_key, :type => String
  field :ami_id, :type => String
end
