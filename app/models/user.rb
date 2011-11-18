class User
  include Mongoid::Document
  # Include default devise modules. Others available are: :token_authenticatable, :encryptable,
  # :confirmable, :lockable, :timeoutable, :database_authenticatable, :registerable, :recoverable,
  # :rememberable, :trackable, :validatable and :omniauthable
  devise :registerable, :trackable

  field :contact, :type => Boolean

  validates_presence_of :email, :contact
  #  validates_uniqueness_of :email, :case_sensitive => false
  attr_accessible :email, :contact
end
