(ns frontend.components.pieces.job
  (:require [clojure.spec :as s :include-macros true]
            [clojure.test.check.generators :as gen]
            [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            ;; Must be required for specs.
            frontend.components.pieces.run-row
            [frontend.components.pieces.status :as status]
            [frontend.datetime :as datetime]
            [frontend.devcards.faker :as faker]
            [frontend.devcards.morphs :as morphs]
            [frontend.routes :as routes]
            [frontend.timer :as timer]
            [frontend.utils :refer-macros [component element html]]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.seq :refer [index-of]]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [devcards.core :as dc :refer [defcard]]))

(defn- status-class [run-status type]
  (case run-status
    (:job-run-status/waiting
     :job-run-status/not-running)
    :status-class/waiting

    :job-run-status/running
    (if (= :job-type/approval type)
      :status-class/on-hold
      :status-class/running)

    :job-run-status/succeeded :status-class/succeeded

    (:job-run-status/failed :job-run-status/timed-out)
    :status-class/failed

    (:job-run-status/canceled
     :job-run-status/not-run)
    :status-class/stopped))

(defui ^:once Requires
  static om-next/IQuery
  (query [this]
    [:job/name])
  Object
  (render [this]
    (component
      (html
       [:div
        [:.heading "Requires"]
        [:ul.requirements
         (for [required-job (om-next/props this)]
           [:li.requirement (:job/name required-job)])]]))))

(def requires (om-next/factory Requires))

(defui ^:once Job
  static om-next/IQuery
  (query [this]
    [:job/id
     :job/status
     :job/type
     :job/started-at
     :job/stopped-at
     :job/name
     {:job/build [:build/vcs-type
                  :build/org
                  :build/repo
                  :build/number]}
     {:job/required-jobs (om-next/get-query Requires)}
     {:job/run [:run/id]}])
  Object
  (render [this]
    (component
      (let [{:keys [job/id
                    job/status
                    job/type
                    job/started-at
                    job/stopped-at
                    job/required-jobs]
             {:keys [build/vcs-type
                     build/org
                     build/repo
                     build/number]
              :as build} :job/build
             job-name :job/name
             {run-id :run/id} :job/run}
            (om-next/props this)]
        (card/full-bleed
         (element :content
           (html
            [:div
             [:.job-card-inner
              [:.body
               [:.status-name
                [:.status (status/icon (status-class status type))]
                (if (nil? build)
                  job-name
                  [:a {:href
                       (routes/v1-build-path vcs-type
                                             org
                                             repo
                                             nil
                                             number)}
                   job-name])]
               (when (= :job-type/approval type)
                 [:.approval
                  (button/button
                   {:kind :primary
                    :disabled? (not= :job-run-status/running status)
                    :on-click #(om-next/transact! this [`(job/approve {:job/id ~id :run/id ~run-id})])}
                   (case status
                     :job-run-status/succeeded "Approved"
                     :job-run-status/failed "Declined"
                     "Approve"))])
               (when (seq required-jobs)
                 [:.requires
                  (requires required-jobs)])]
              [:.job-metadata
               [:.metadata-row
                [:.metadata-item
                 [:i.material-icons "today"]
                 (if started-at
                   [:span {:title (str "Started: " (datetime/full-datetime started-at))}
                    (build-legacy common/updating-duration {:start started-at} {:opts {:formatter datetime/time-ago-abbreviated}})
                    [:span " ago"]]
                   "-")]
                [:.metadata-item
                 [:i.material-icons "timer"]
                 (if stopped-at
                   [:span {:title (str "Duration: " (datetime/as-duration (- stopped-at started-at)))}
                    (build-legacy common/updating-duration {:start started-at
                                                            :stop stopped-at})]
                   "-")]]]]])))))))

(def job (om-next/factory Job {:keyfn :job/id}))

(dc/do
  (s/def :circleci/status-class
    #{:status-class/waiting
      :status-class/running
      :status-class/succeeded
      :status-class/failed
      :status-class/stopped})

  (s/def :job/entity (s/and
                      (s/keys :req [:job/id
                                    :job/status
                                    :job/type
                                    :job/started-at
                                    :job/stopped-at
                                    :job/name
                                    :job/build
                                    :job/required-jobs
                                    :job/run])
                      (fn [{:keys [:job/status :job/started-at :job/stopped-at]}]
                        (case status
                          (:job-run-status/not-run
                           :job-run-status/waiting)
                          (and (nil? started-at) (nil? stopped-at))

                          (:job-run-status/running)
                          (and started-at (nil? stopped-at))

                          (:job-run-status/succeeded
                           :job-run-status/failed
                           :job-run-status/timed-out
                           :job-run-status/canceled)
                          (and started-at stopped-at (< started-at stopped-at))))))

  (s/def :job/id uuid?)

  (s/def :job/status #{:job-run-status/succeeded
                       :job-run-status/failed
                       :job-run-status/timed-out
                       :job-run-status/canceled
                       :job-run-status/not-run
                       :job-run-status/running
                       :job-run-status/waiting})

  (s/def :job/type #{:job-type/build
                     :job-type/approval})

  (s/def :job/started-at (s/nilable inst?))
  (s/def :job/stopped-at (s/nilable inst?))
  (s/def :job/name (s/and string? seq))
  (s/def :job/required-jobs (s/every (s/keys :req [:job/name])))
  (s/def :job/run :run/entity)

  (s/def :job/build (s/keys :req [:build/vcs-type
                                  :build/org
                                  :build/repo
                                  :build/number]))

  (s/def :build/vcs-type #{:github :bitbucket})
  (s/def :build/org (s/and string? seq))
  (s/def :build/repo (s/and string? seq))
  (s/def :build/number pos-int?)

  (s/fdef job
    :args (s/cat :job :job/entity))

  (s/fdef requires
    :args (s/cat :jobs :job/required-jobs))


  (defcard job-cards
    (let [statuses [:job-run-status/not-run
                    :job-run-status/waiting
                    :job-run-status/running
                    :job-run-status/succeeded
                    :job-run-status/failed
                    :job-run-status/timed-out
                    :job-run-status/canceled]]
      (morphs/render #'job {:job/name #(faker/snake-case-identifier 3 7)
                            ;; ::s/pred targets the case where the value is non-nil.
                            [:job :job/started-at ::s/pred] #(faker/inst-in-last-day)
                            [:job :job/stopped-at ::s/pred] #(faker/inst-in-last-day)
                            :job/required-jobs #(gen/vector (s/gen (s/keys :req [:job/name])
                                                                   {:job/name (constantly (faker/snake-case-identifier 3 7))})
                                                            0 5)}
                     (fn [morphs]
                       (binding [om-next/*shared* {:timer-atom (timer/initialize)}]
                         (html
                          [:div {:style {:width 500
                                         :margin "auto"}}
                           (card/collection (->> morphs
                                                 (sort-by #(->> % first :job/status (index-of statuses)))
                                                 (map (partial apply job))))]))))))

  (defcard requires
    (morphs/render #'requires {[:jobs] (gen/vector (s/gen (s/keys :req [:job/name])
                                                          {:job/name #(faker/snake-case-identifier)})
                                                   1 5)}
                   (fn [morphs]
                     (card/collection (->> morphs
                                           (sort-by (comp count first))
                                           (map (partial apply requires))))))))
