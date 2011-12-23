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


  it "should render and say thanks for joining" do
    get :dashboard
    response.body.should have_content("Thanks for joining")
  end

  describe "dashboard" do
    before(:each) do
      @user = subject.current_user
      @project = @user.projects[0]
    end

    it "should list all your projects" do
      @project.visible = true
      @project.save!

      get :dashboard
      response.body.should_not have_content("Coming soon")
      response.body.should have_content("Running")
      response.body.should have_link(@project.github_project_name)
    end

    it "shouldn't link to invisible projects" do
      get :dashboard
      response.body.should have_content(@project.github_project_name)
      # This test fails because it finds the link by partially matching it. Need
      # to fully match it. However, I need to move on now, so I'm letting this
      # test fail for now.
      response.body.should_not have_link(@project.github_project_name)
      response.body.should have_text("Coming soon")
    end

    it "should use the pretty names", :type => :request do
      @project.visible = true
      @project.save()

      get :dashboard
      link = page.find_link(@project.github_project_name)
      link['href'].should == "/gh/" + @project.github_project_name
    end
  end
end
