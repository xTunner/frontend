require 'spec_helper'
require 'json'

sample = {
  "before" => "5aef35982fb2d34e9d9d4502f6ede1072793222d",
  "repository" => {
    "url" => "http://github.com/defunkt/github",
    "name" => "github",
    "description" => "You're lookin' at it.",
    "watchers" => 5,
    "forks" => 2,
    "private" => 1,
    "owner" => {
      "email" => "chris@ozmm.org",
      "name" => "defunkt"
    }
  },
  "commits" => [
                {
                  "id" => "41a212ee83ca127e3c8cf465891ab7216a705f59",
                  "url" => "http =>//github.com/defunkt/github/commit/41a212ee83ca127e3c8cf465891ab7216a705f59",
                  "author" => {
                    "email" => "chris@ozmm.org",
                    "name" => "Chris Wanstrath"
                  },
                  "message" => "okay i give in",
                  "timestamp" => "2008-02-15T14 =>57 =>17-08 =>00",
                  "added" => ["filepath.rb"]
                },
                {
                  "id" => "de8251ff97ee194a289832576287d6f8ad74e3d0",
                  "url" => "http =>//github.com/defunkt/github/commit/de8251ff97ee194a289832576287d6f8ad74e3d0",
                  "author" => {
                    "email" => "chris@ozmm.org",
                    "name" => "Chris Wanstrath"
                  },
                  "message" => "update pricing a tad",
                  "timestamp" => "2008-02-15T14 =>36 =>34-08 =>00"
                }],
  "after" => "de8251ff97ee194a289832576287d6f8ad74e3d0",
  "ref" => "refs/heads/master"
}


sample["repository"]["url"] = "https://github.com/arohner/circle-dummy-project"
sample["commits"] = [{ "id" => "78f58846a049bb6772dcb298163b52c4657c7d45",
                       "url" => "http://github.com/arohner/circle-dummy-project/commit/78f58846a049bb6772dcb298163b52c4657c7d45",
                       "author" => {
                         "email" => "arohner@gmail.com",
                         "name" => "Allen Rohner"
                       },
                       "message" => "okay i give in",
                       "timestamp" => "2008-02-15T14 =>57 =>17-08 =>00",
                       "added" => ["filepath.rb"]}]
dummy_json = JSON.generate(sample)

# make a clone to test deleted branches
deleted = JSON.parse(dummy_json)
deleted["after"] = "0000000000000000000000000000000000000000"
deleted_json = JSON.generate(deleted)


java_import "clojure.lang.Var"

def symbolize(s)
  RT.var("clojure.core", "symbol").invoke(s)
end

user_ns = RT.var("clojure.core", "find-ns").invoke(symbolize("user"))
Var.intern(user_ns, symbolize("foo"), dummy_json)

describe GithubController do

  let(:vcs_url) { "https://github.com/arohner/circle-dummy-project" }
  let(:project) { Project.create! :vcs_url => vcs_url }


  it "The github hook successfully triggers builds" do

    p = Project.from_url project.vcs_url
    p.should_not be_nil

    pre_count = Build.where(:vcs_url => vcs_url).length
    post :create, :payload => dummy_json

    post_count = Build.where(:vcs_url => vcs_url).length
    (post_count > pre_count).should be_true
  end

  it "should save where" do
    post :create, :payload => dummy_json
    build = Build.where(:vcs_url => vcs_url).first
    build.should_not == nil
    build.why.should == "github"
    build.user.should == nil
  end

  it "shouldn't trigger when branches are deleted" do
    pre_count = Build.where(:vcs_url => vcs_url).length
    post :create, :payload => deleted_json
    sleep 1
    post_count = Build.where(:vcs_url => vcs_url).length
    post_count.should == pre_count
  end

  it "should fail due to invalid json" do
    lambda {
      post :create, :payload => "invalid json"
    }.should raise_error
  end

  it "should fail due to empty json" do
    lambda {
      post :create, :payload => JSON.generate({})
    }.should raise_error
  end
end
