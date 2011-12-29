require 'spec_helper'

describe UsersController do
  login_user
  render_views
  let(:page) { Capybara::Node::Simple.new(@response.body) }


  it "should have a current_user" do
    subject.current_user.should_not be_nil
  end

  describe "routes", :type => :routing do
    it "should render the right action" do
      pending "I dont know how to test this one properly"
      {:get => "/"}.should route_to(:controller => 'users', :action => "dashboard")
    end
  end


  it "should render and have text" do
    get :dashboard
    response.body.should have_content("Your builds")
  end

  describe "dashboard" do
    before(:each) do
      @user = subject.current_user
      @project = @user.projects[0]
    end

    it "should list all your projects" do
      get :dashboard
      response.body.should have_content("Available")
      response.body.should have_link(@project.github_project_name)
    end

    it "should use the pretty names", :type => :request do
      get :dashboard
      link = page.find_link(@project.github_project_name)
      link['href'].should == "/gh/" + @project.github_project_name
    end
  end
end
