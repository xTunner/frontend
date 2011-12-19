class Project
  include Mongoid::Document
  field :name
  field :vcs_url
  field :ssh_private_key
  field :ssh_public_key
  field :visible, :type => Boolean, :default => false

  has_and_belongs_to_many :users

  has_many :jobs # TECHNICAL_DEBT: s/jobs/builds/

  attr_accessible :name, :vcs_url
end
