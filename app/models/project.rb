class Project
  include Mongoid::Document
  field :name, :type => String
  field :vcs_url, :type => String
  field :ssh_private_key
  field :ssh_public_key

  has_many :jobs # TECHNICAL_DEBT: s/jobs/builds/
end
