class User
  include Mongoid::Document
  # Include default devise modules. Others available are: :token_authenticatable, :encryptable,
  # :confirmable, :lockable, :timeoutable, :database_authenticatable, :registerable, :recoverable,
  # :rememberable, :trackable, :validatable and :omniauthable
  devise :trackable, :database_authenticatable, :recoverable, :rememberable

  field :name
  field :contact, :type => Boolean
  field :email, :type => String
  field :admin, :type => Boolean, :default => false
  field :github_access_token
  field :signup_channel, :default => "unknown"
  field :signup_referer, :default => "unknown"

  validates_presence_of :email, :contact, :name
  #  validates_uniqueness_of :email, :case_sensitive => false
  attr_accessible :name, :contact, :email, :password, :password_confirmation
end
