require 'factory_girl'

FactoryGirl.define do

  factory :user do
    name 'Test User'
    email 'user@test.com'
    password 'please'

    factory :github_user do
      # This user doesnt have a username or email set up in their profile
      email 'builds@circleci.com'
      password 'engine process vast trace'
      name 'Circle Dummy user'
    end

    factory :admin_user do
      admin true
    end

    factory :email_hater do
      name "Email hater"
      email "hatesemail@circleci.com"
      email_preferences {}
    end

    factory :email_lover do
      name "Email lover"
      email "lovesemail@circleci.com"
      email_preferences {}
    end
  end


  factory :project do
    vcs_url "https://github.com/circleci/circle-dummy-project"

    factory :unowned_project do
      vcs_url "https://github.com/circleci/circle-dummy-project2"
    end

    factory :project_with_weird_characters do
      vcs_url "https://github.com/._-_.-/-.__-.-/"
    end

    factory :project_with_specs do
      vcs_url "https://github.com/circleci/project_with_specs"
      setup "echo do setup"
      dependencies "echo do dependencies"
      compile "echo do compile"
      test "echo do test"
      # extra is a new field, but no-one put anything in compile so we're fine.
      extra "echo do extra"
    end

  end

  factory :action_log do

    factory :successful_log do
      name "ls -l"
      exit_code 0
      out []
    end

    factory :failing_log do
      name "not-a-real-program"
      exit_code 127
      out []
    end
  end

  factory :build do
    vcs_url "https://github.com/circleci/circle-dummy-project"
    vcs_revision "abcdef123456789"
    start_time Time.now - 10.minutes
    stop_time Time.now
    committer_email "user@test.com"
    subject "That's right, I wrote some code"
    build_num 1

    factory :successful_build do
      failed false
    end

    factory :failing_build do
      failed true
    end

    factory :last_build do
    end

    factory :fixed_build do
    end
  end


  factory :signup do
    email "test@email.com"
    contact "true"
  end
end
