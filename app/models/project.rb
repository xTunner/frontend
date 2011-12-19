class Project
  include Mongoid::Document
  include Mongoid::Slugify

  field :name
  field :vcs_url
  field :ssh_private_key
  field :ssh_public_key
  field :visible, :type => Boolean, :default => false

  has_and_belongs_to_many :users
  has_many :jobs # TECHNICAL_DEBT: s/jobs/builds/

  attr_accessible :name, :vcs_url


  def generate_slug
    # This has a '/' in it, so the constraints also needed to be loosened in config/routes.rb
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
