require 'spec_helper'

describe HomeController do
  render_views

  describe "GET 'index'" do
    it "returns http success" do
      get 'index'
      response.should be_success
      response.body.should have_selector("title", :content => "Circle - Continuous Integration made easy")
    end
  end
end
