class Project
  include Mongoid::Document

  field :name
  field :vcs_url
  field :ssh_private_key
  field :ssh_public_key
  field :visible, :type => Boolean, :default => false

  has_and_belongs_to_many :users
  has_many :builds

  attr_accessible :name, :vcs_url

  # TECHNICAL_DEBT - TODO - WARNING - HACK - BEWARE
  # I can't quite figure out how this looks the project up, so it must be a linear search!
  def to_param
    github_project_name
  end

  def github_project_name
    result = vcs_url.sub("https://github.com/", "")
    if result[-1] == "/" then
      result = result[0..-2]
    end
    result
  end

end
