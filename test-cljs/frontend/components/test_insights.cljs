(ns frontend.components.test-insights
  (:require [cemerick.cljs.test :as test]
            [frontend.test-utils :as test-utils]
            [frontend.utils :as utils :refer [sel1 sel]]
            [frontend.utils.docs :as doc-utils]
            [frontend.stefon :as stefon]
            [goog.dom]
            [frontend.components.insights :as insights]
            [om.core :as om :include-macros true])
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var)]))

(def test-projects-data
  [{:reponame "test repo"
    :username "foo user"
    :default_branch "master"
    :recent_builds [
                    {
                     :committer_name "Mr. M",
                     :usage_queued_at "2015-09-14T04:25:05.652Z",
                     :branch "master",
                     :outcome "failed",
                     :body "Enterprise admins",
                     :start_time "2015-09-14T04:24:59.767Z",
                     :lifecycle "finished",
                     :stop_time "2015-09-14T04:33:58.898Z",
                     :subject "Merge pull request #5052 from circleci/enterprise-odds-and-ends",
                     :vcs_url "https://github.com/circleci/circle",
                     :vcs_tag nil,
                     :build_num 129611,
                     :author_date "2015-09-14T00:24:57-04:00",
                     :status "failed",
                     :build_time_millis 539131,
                     :queued_at "2015-09-14T04:24:59.507Z",
                     :vcs_revision "3bb775e8006351380fe3a025ab87c2cc556f4ff5",
                     :committer_date "2015-09-14T00:24:57-04:00",
                     :reponame "circle",
                     :build_url "https://circleci.com/gh/circleci/circle/129611",
                     :username "circleci",
                     :dont_build nil
                     },
                    {
                     :vcs_url "https://github.com/circleci/circle",
                     :vcs_tag nil,
                     :author_date "2015-09-12T23:27:08-04:00",
                     :build_num 129596,
                     :build_time_millis 702991,
                     :status "success",
                     :queued_at "2015-09-13T03:28:13.057Z",
                     :committer_date "2015-09-12T23:27:08-04:00",
                     :vcs_revision "f18aa5183b93cd0c8756cf00c51d40f51402b5b0",
                     :reponame "circle",
                     :build_url "https://circleci.com/gh/circleci/circle/129596",
                     :dont_build nil,
                     :username "circleci",
                     :usage_queued_at "2015-09-13T03:28:16.801Z",
                     :branch "master",
                     :outcome "success",
                     :body "Have an up-check for monitoring purposes",
                     :stop_time "2015-09-13T03:39:56.305Z",
                     :start_time "2015-09-13T03:28:13.314Z",
                     :lifecycle "finished",
                     :subject "Merge pull request #5051 from circleci/nginx-upcheck"
                     }
                    ]}])

(deftest can-render-feature-container
  (let [test-node (goog.dom/htmlToDocumentFragment "<div></div>")]
    (om/root insights/build-insights test-projects-data {:target test-node})
    (testing "Simple render of feature container.")))


(deftest can-render-project-block
  (let [test-node (goog.dom/htmlToDocumentFragment "<div></div>")]
    (om/root insights/project-insights (first test-projects-data) {:target test-node})
    (testing "Simple render of feature container.")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TODO: Async testing does not work right now, it's not in scope to fix it ;;
;; here                                                                     ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (deftest can-render-insights-bar
;;   (let [test-node (goog.dom/htmlToDocumentFragment "<div class=\"test-chart\"></div>")
;;         chartable-builds (insights/chartable-builds (:recent_builds (first test-projects-data)))]
;;     (.appendChild (.-body js/document) test-node)
;;     (insights/visualize-insights-bar! test-node chartable-builds)
;;     (testing "Renders legend."
;;       (is (= "Passed"
;;              (utils/text (sel1 ".test-chart .legend.left")))))
;;     (testing "Renders correct number of bars."
;;       (is (= 4 (.-length (sel "rect.bar")))))))