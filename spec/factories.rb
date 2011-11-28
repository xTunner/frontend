require 'factory_girl'

Factory.define :user do |u|
  u.name 'Test User'
  u.email 'user@test.com'
  u.password 'please'
end

Factory.define :signup do |s|
  s.email "test@email.com"
  s.contact "true"
end
