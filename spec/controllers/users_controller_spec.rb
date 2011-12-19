require 'spec_helper'

describe UsersController do
  login_user
  render_views

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

  it "should list all your projects" do
    user = subject.current_user
    project = user.projects[0]
    project.visible = true
    project.save()

    get :dashboard
    response.body.should_not have_content("Coming soon")
    response.body.should have_content("Running")
    response.body.should have_link(project.name)
  end

  it "shouldn't link to invisible projects" do
    user = subject.current_user
    project = user.projects[0]

    get :dashboard
    response.body.should have_content(project.name)
    response.body.should_not have_link(project.name)
    response.body.should have_content("Coming soon")
  end

end
