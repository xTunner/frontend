class Signup
  include Mongoid::Document
  include Mongoid::Timestamps
  field :email, :type => String
  field :contact, :type => Boolean
end
