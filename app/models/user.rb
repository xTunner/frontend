class User
  include Mongoid::Document
  include Mongoid::Timestamps

  # Include default devise modules. Others available are: :token_authenticatable, :encryptable,
  # :confirmable, :lockable, :timeoutable, :recoverable,
  # :rememberable, :trackable, :validatable and :omniauthable
  devise :trackable, :database_authenticatable, :recoverable, :rememberable, :registerable

  field :name
  field :contact, :type => Boolean
  field :admin, :type => Boolean, :default => false
  field :github_access_token
  field :signup_channel
  field :signup_referer

  field :sent_first_build_email, :default => false
  field :email_preferences, :type => Hash, :default => {
    # The three options are to send:
    # - :author => if you are the :author/committer,
    # - :branch => if it's in a :branch you follow (everyone follows master),
    # - :all => for all builds.
    "on_fail" => ["author", "branch"], # when builds :fail, or if they were just fixed
    "on_success" => ["author"], # when builds are :successful
  }


  # For making the form nicer, we try to prefetch these from github. When
  # they're not available in time, we need a default.
  field :fetched_name, :default => ""
  field :fetched_email, :default => ""

  has_and_belongs_to_many :projects

  validates_presence_of :email
  validates_uniqueness_of :email, :case_sensitive => false

  attr_accessible :name, :contact, :email, :password, :password_confirmation, :email_preferences


  def known_email
    if is_guest?
      "!!<#{fetched_email}>!!"
    else
      email
    end
  end

  # TECHNICAL_DEBT: After manually updating the DB, remove the sign_up_date. We
  # don't have this field for all our users, so set it to around the time we
  # added a bunch. We can manually update them later.
  def signup_at
    created_at || Time.new(2011, 12, 14, 22, 02, 15)
  end

  # Latest builds for a project the user is a member of
  def latest_project_builds
    # TECHINCAL_DEBT: fucking hell, is there any way to do this that isn't really really expensive?
    ps = projects
    builds = []
    ps.each do |p|
      builds += p.recent_builds(30)
    end

    return builds.sort_by! { |b| b.start_time }.reverse.slice(0, 20)
  end

  def is_guest?
    !email.include?("@")
  end


  # https://github.com/plataformatec/devise/wiki/How-To:-Allow-users-to-edit-their-account-without-providing-a-password
  # Guest users start with a blank password, but we ask them to update them
  # later. However, without this hack, they won't be able to validate their
  # blank password, and devise will throw a caniption.
  def update_with_password(params = {})
    update_attributes(params)
  end

  # True if there has been at least one build in every project the user owns
  def build_in_every_project?
    self.projects.all? {|p| p.latest_build}
  end

  def wants_build_email?(build)
    # No-one gets email until they get their first email.
    #return false unless sent_first_build_email # but not until we're actually sending the first email.

    is_author =
      build.committer_email == email ||
      build.author_email == email ||
      build.committer_name == name ||
      build.author_name == name

    # For now, we just assume everyone follow master
    follows_this_branch = build.branch == "master"

    prefs = email_preferences
    prefs["on_success"] ||= []
    prefs["on_fail"] ||= []

    # TECHNICAL_DEBT: refactor
    send = false
    send ||= build.failed && (is_author && prefs["on_fail"].include?("author"))
    send ||= build.failed && (follows_this_branch && prefs["on_fail"].include?("branch"))
    send ||= build.failed && (prefs["on_fail"].include?("all"))
    send ||= !build.failed && (is_author && prefs["on_success"].include?("author"))
    send ||= !build.failed && (follows_this_branch && prefs["on_success"].include?("branch"))
    send ||= !build.failed && (prefs["on_success"].include?("all"))

    return send
    end
end
