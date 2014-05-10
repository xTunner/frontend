(ns frontend.components.project-settings
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.components.common :as common]
            [frontend.utils :as utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(def sidebar
  [:ul.side-list
   [:li.side-title "Project Settings"]
   [:li [:a {:href "edit"} "Overview"]]
   [:li.side-title "Tweaks"]
   [:li [:a {:href "#parallel-builds"} "Parallelism"]]
   [:li [:a {:href "#env-vars"} "Environment variables"]]
   [:li.side-title "Test Commands"]
   [:li [:a {:href "#setup"} "Dependencies"]]
   [:li [:a {:href "#tests"} "Tests"]]
   [:li.side-title "Notifications"]
   [:li [:a {:href "#hooks"} "Chatrooms"]]
   [:li [:a {:href "#webhooks"} "Webhooks"]]
   [:li.side-title "Permissions"]
   [:li [:a {:href "#ssh"} "SSH keys"]]
   [:li [:a {:href "#github"} "GitHub user"]]
   [:li [:a {:href "#api"} "API tokens"]]
   [:li.side-title "Build Artifacts"]
   [:li [:a {:href "#artifacts"} "Artifacts"]]
   [:li.side-title "Continuous Deployment"]
   [:li [:a {:href "#heroku"} "Heroku"]]
   [:li [:a {:href "#deployment"} "Other Deployments"]]])

(defn overview [project controls-ch]
  [:div.project-settings-block
   [:h2 "How to configure " [:span (vcs-url/project-name (:vcs_url project))]]
   [:ul.overview-options
    [:li.overview-item
     [:h4 "Option 1"]
     [:p
      "Do nothing! Circle infers many settings automatically. Works great for Ruby, Python, NodeJS, Java and Clojure. However, if it needs tweaks or doesn't work, see below."]]
    [:li.overview-item
     [:h4 "Option 2"]
     [:p
      "Override inferred settings and add new test commands "
      [:a {:href "#setup"} "through the web UI"]
      ". This works great for prototyping changes."]]
    [:li.overview-item
     [:h4 "Option 3"]
     [:p
      "Override all settings via a "
      [:a {:href "/docs/configuration"} "circle.yml file"]
      " in your repo. Very powerful."]]]])

(defn parallel-builds [project controls-ch]
  [:div "parallel-builds"])

(defn env-vars [project controls-ch]
  [:div "env vars"])

(defn dependencies [project controls-ch]
  [:div "deps"])

(defn tests [project controls-ch]
  [:div "tests"])

(defn chatrooms [project controls-ch]
  [:div "chatrooms"])

(defn webhooks [project controls-ch]
  [:div "webhooks"])

(defn ssh-keys [project controls-ch]
  [:div "ssh-keys"])

(defn github-user [project controls-ch]
  [:div "github-user"])

(defn api-tokens [project controls-ch]
  [:div "api tokesn"])

(defn artifacts [project controls-ch]
  [:div "artifacts"])

(defn heroku [project controls-ch]
  [:div "heroku"])

(defn other-deployment [project controls-ch]
  [:div "deployment"])

(defn subpage-fn [subpage]
  (get {:parallel-builds parallel-builds
        :env-vars env-vars
        :setup dependencies
        :tests tests
        :hooks chatrooms
        :webhooks webhooks
        :ssh ssh-keys
        :github github-user
        :api api-tokens
        :artifacts artifacts
        :heroku heroku
        :deployment other-deployment}
       subpage
       overview))

(defn follow-sidebar [project controls-ch]
  (let [project-id (project-model/id project)
        vcs-url (:vcs_url project)]
    [:div.follow-status
     [:div.followed
      ;; this is weird, but it's what the css expectss
      (when (:followed project)
        (list
         [:i.fa.fa-group]
         [:h4 "You're following this repo"]
         [:p
          "We'll keep an eye on this and update you with personalized build emails. "
          "You can stop these any time from your "
          [:a {:href "/account"} "account settings"]
          "."]
         ;; XXX make unfollow work!
         [:button {:on-click #(put! controls-ch [:unfollowed-repo project-id])}
          "Unfollow"]))]
     [:div.not-followed
      (when-not (:followed project)
        (list
         [:h4 "You're not following this repo"]
         [:p
          "We can't update you with personalized build emails unless you follow this project. "
          "Projects are only tested if they have a follower."]
         [:button {:on-click #(put! controls-ch [:follow-repo {:vcs_url vcs-url}])}
          "Follow"]))]]))

(defn project-settings [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [project (:current-project data)
            subpage (:project-settings-subpage data)
            controls-ch (get-in opts [:comms :controls])]
        (html
         (if-not project
           [:div.loading-spinner common/spinner]
           [:div#project-settings
            [:aside sidebar]
            [:div.project-settings-inner
             (common/flashes)
             [:div#subpage
              ((subpage-fn subpage) project controls-ch)]]
            (follow-sidebar project controls-ch)]))))))
