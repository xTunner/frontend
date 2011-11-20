require 'spec_helper'

describe Signup do

  it "should handle a normal case" do
    Signup.create!({:email => "anemail@asd.com", :contact => "true"})
  end

  it "shouldn't fail if it's not normal" do
    Signup.create!({})
    Signup.create!({:other => 'x'})
    Signup.create!({:email => 5})
    Signup.create!({:contact => true})
  end

end
