class User
  include Mongoid::Document
  # Include default devise modules. Others available are: :token_authenticatable, :encryptable,
  # :confirmable, :lockable, :timeoutable, :database_authenticatable, :recoverable,
  # :rememberable, :trackable, :validatable and :omniauthable
  devise :trackable, :database_authenticatable, :recoverable, :rememberable, :registerable

  field :name
  field :contact, :type => Boolean
  field :admin, :type => Boolean, :default => false
  field :github_access_token
  field :signup_channel
  field :signup_referer

  # For making the form nicer, we try to prefetch these from github. When
  # they're not available in time, we need a default.
  field :fetched_name, :default => ""
  field :fetched_email, :default => ""

  has_and_belongs_to_many :projects

  validates_presence_of :email
  validates_uniqueness_of :email, :case_sensitive => false
  attr_accessible :name, :contact, :email, :password, :password_confirmation

  # https://github.com/plataformatec/devise/wiki/How-To:-Allow-users-to-edit-their-account-without-providing-a-password
  # Guest users start with a blank password, but we ask them to update them
  # later. However, without this hack, they won't be able to validate their
  # blank password, and devise will throw a caniption.
  def update_with_password(params = {})
    update_attributes(params)
  end


end
