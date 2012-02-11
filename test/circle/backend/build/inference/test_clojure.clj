(ns circle.backend.build.inference.test-clojure
  (:use midje.sweet)
  (:use circle.backend.build.inference.clojure)
  (:require [circle.backend.build.test-utils :as test])
  (:require fs)
  (:require [circle.backend.git :as git]))

(fact "`lein deps` is always run as a :setup command."
      (let [repo (test/test-repo "leiningen_project")
            inferred-actions (spec repo)]
        inferred-actions => (contains #(and (= "lein deps" (:name %))
                                            (= :setup (:type %))))))

(future-fact "`lein native` is  run as a :setup command when there are native dependencies.")

(fact "When Midje is a dependency, `lein midje` is the :test command."
     (let [repo (test/test-repo "midje_project")
                inferred-actions (spec repo)]
       inferred-actions => (contains #(and (= "lein midje" (:name %))
                                           (= :test (:type %))))))

(fact "When Midje is not a dependency, `lein test` is the :test command."
     (let [repo (test/test-repo "leiningen_project")
                inferred-actions (spec repo)]
       inferred-actions => (contains #(and (= "lein test" (:name %))
                                           (= :test (:type %))))))
