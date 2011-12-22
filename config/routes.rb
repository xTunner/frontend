MongoidTest::Application.routes.draw do

  # User authentication
  devise_for :users, :path => '/',
    :path_names => {:sign_in => 'login', :sign_out => 'logout', :sign_up => "welcome"},
    :controllers => {:registrations => "registrations"}


  match '/gh/:project', :to => 'projects#show', :as => :github_project, :constraints => { :project => /[a-z\/\-A-Z0-9_]+/ }, :via => [:get]

  match '/gh/:project', :to => 'builds#create', :as => :project_builds, :constraints => { :project => /[a-z\/\-A-Z0-9_]+/ }, :via => [:post]

  match '/admin', :to => 'admin#show', :as => :admin

  # TECHNICAL_DEBT: these aren't RESTful, and they're not clean

  # Main project UI
  resources :projects, :only => [:show]

  # Signup form (for repositories)
  match '/join/dynamic', :to => 'join#dynamic', :via => [:get], :as => :join_dynamic
  match '/join', :to => 'join#all', :via => [:get], :as => :join
  match '/join', :to => 'join#add_projects', :via => [:post], :as => :join_form

  # Github post-commit hook
  match '/hooks/github', :to => 'github#create', :via => [:post]

  # Homepage (landing page for un-logged-in users)
  unauthenticated do
    as :user do
      # pre-launch signup form's target
      match '/notify-signup', :to => 'home#create', :action => :create, :via => [:post], :as => :notify_signup

      # homepage
      root :to => "home#index"
    end
  end

  # Default page for signed-in users.
  root :to => "users#dashboard"


  # The priority is based upon order of creation:
  # first created -> highest priority.

  # Sample of regular route:
  #   match 'products/:id' => 'catalog#view'
  # Keep in mind you can assign values other than :controller and :action

  # Sample of named route:
  #   match 'products/:id/purchase' => 'catalog#purchase', :as => :purchase
  # This route can be invoked with purchase_url(:id => product.id)

  # Sample resource route (maps HTTP verbs to controller actions automatically):
  #   resources :products

  # Sample resource route with options:
  #   resources :products do
  #     member do
  #       get 'short'
  #       post 'toggle'
  #     end
  #
  #     collection do
  #       get 'sold'
  #     end
  #   end

  # Sample resource route with sub-resources:
  #   resources :products do
  #     resources :comments, :sales
  #     resource :seller
  #   end

  # Sample resource route with more complex sub-resources
  #   resources :products do
  #     resources :comments
  #     resources :sales do
  #       get 'recent', :on => :collection
  #     end
  #   end

  # Sample resource route within a namespace:
  #   namespace :admin do
  #     # Directs /admin/products/* to Admin::ProductsController
  #     # (app/controllers/admin/products_controller.rb)
  #     resources :products
  #   end

  # You can have the root of your site routed with "root"
  # just remember to delete public/index.html.
  # root :to => 'welcome#index'

  # See how all your routes lay out with "rake routes"

  # This is a legacy wild controller route that's not recommended for RESTful applications.
  # Note: This route will make all actions in every controller accessible via GET requests.
  # match ':controller(/:action(/:id(.:format)))'
end
