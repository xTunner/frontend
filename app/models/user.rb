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

  has_and_belongs_to_many :projects

  validates_presence_of :email
  validates_uniqueness_of :email, :case_sensitive => false
  attr_accessible :name, :contact, :email, :password, :password_confirmation

  def update_with_password(params = {})
    puts "User update_w_password: #{params}"
    if params[:password].blank?
      params.delete(:password)
      params.delete(:password_confirmation) if params[:password_confirmation].blank?
    end
    update_attributes(params)
  end


end
