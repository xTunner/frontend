require 'spec_helper'

describe HomeController do
  render_views

  let(:signup) do { email: "test@email.com", contact: true } end

  describe "GET 'index'" do
    it "returns http success" do
      get 'index'
      response.should be_success
      response.body.should have_selector("title",
                                         :content => "Circle - Continuous Integration made easy")
    end

    it "signs up OK" do
      lambda do
        post :create, :signup => signup
      end.should change(Signup, :count).by(1)
    end

    it "redirects" do
      post :create, :signup => signup
      response.should redirect_to(root_path)
      flash[:done].should == true
    end
  end
end
