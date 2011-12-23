class Project
  include Mongoid::Document

  field :vcs_url
  field :ssh_private_key
  field :ssh_public_key
  field :visible, :type => Boolean, :default => false

  has_and_belongs_to_many :users
  has_many :builds

  attr_accessible :name, :vcs_url

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

  def config
    # For now, just read circle.yml for everyone, and see what happens.
    File.read("#{File.dirname(__FILE__)}/../../circle.yml")
  end
end
