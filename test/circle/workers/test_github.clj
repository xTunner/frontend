(ns circle.workers.test-github
  (:require circle.backend.build.test-utils)
  (:require [circle.backend.build :as build])
  (:use midje.sweet)
  (:use circle.workers.github))

(fact "start-build-from-hook works with dummy project"
  (circle.backend.build.test-utils/ensure-test-project)
  (let [json "{\"before\":\"5aef35982fb2d34e9d9d4502f6ede1072793222d\",\"repository\":{\"url\":\"https://github.com/arohner/circle-dummy-project\",\"name\":\"github\",\"description\":\"You're lookin' at it.\",\"watchers\":5,\"forks\":2,\"private\":1,\"owner\":{\"email\":\"chris@ozmm.org\",\"name\":\"defunkt\"}},\"commits\":[{\"id\":\"78f58846a049bb6772dcb298163b52c4657c7d45\",\"url\":\"https://github.com/arohner/circle-dummy-project/commit/78f58846a049bb6772dcb298163b52c4657c7d45\",\"author\":{\"email\":\"arohner@gmail.com\",\"name\":\"Allen Rohner\"},\"message\":\"okay i give in\",\"timestamp\":\"2008-02-15T14 =>57 =>17-08 =>00\",\"added\":[\"filepath.rb\"]}],\"after\":\"de8251ff97ee194a289832576287d6f8ad74e3d0\",\"ref\":\"refs/heads/master\"}"
        build (start-build-from-hook nil nil nil json)]
    (-> build (build/successful?)) => true
    (-> @build :build_num) => integer?))

(fact "authorization-url works"
  (-> "http://localhost:3000/hooks/repos" authorization-url) =>
  "https://github.com/login/oauth/authorize?client_id=586bf699b48f69a09d8c&scope=repo&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fhooks%2Frepos")