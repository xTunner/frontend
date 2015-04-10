(ns frontend.components.changelog
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.components.about :as about]
            [frontend.components.common :as common]
            [frontend.components.shared :as shared]
            [frontend.datetime :as datetime]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(defn changelog [app owner]
  (reify
    om/IRender
    (render [_]
      (let [changelog (get-in app state/changelog-path)
            team (about/team)
            show-id (:show-id changelog)]
        (html
         [:div.changelog.page
          [:div.jumbotron
           common/language-background
           [:div.container
            [:div.col-xs-12
             [:div.text-center
              [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]]
             [:h1.text-center "Changelog"]
             [:h3.text-center "What's changed in CircleCI recently"]]]]
          [:div.outer-section
           [:section.container
            [:div.entries
             (for [entry (:entries changelog)
                   :let [team-member (first (filter #(= (:author entry) (:github %)) team))
                         id (->> entry :guid (re-find #"/changelog/(.+)$") last)]
                   :when (or (nil? show-id) (= show-id id))]
               [:div.row.entry {:id id}
                [:div.hidden-sm.col-md-1.col-md-offset-1.entry-avatar
                 (when team-member
                   [:img {:src (:img-path team-member)}])]
                [:div.col-md-9.entry-main
                 [:div.entry-content
                  [:h3.title
                   [:span.entry-type {:class (:type entry)} (:type entry)]
                   "•"
                   (if show-id
                     (:title entry)
                     [:a.title-text {:href (str "/changelog/" id)} (:title entry)])]
                  [:div.entry-info
                   [:a {:href (:link entry)}
                    " " (datetime/as-time-since (:pubDate entry))]
                   (when team-member
                     (list " by "
                           [:a {:href (if (:twitter team-member)
                                        (str "https://twitter.com/" (:twitter team-member))
                                        (str "https://github.com/" (:github team-member)))}
                            (:name team-member)]))]
                  [:p.description {:dangerouslySetInnerHTML #js {"__html" (:description entry)}}]]]])]
            (when show-id
              [:a {:href "/changelog"} "View Full Changelog"])]]
          [:div.bottom-cta.outer-section.outer-section-condensed
           common/language-background
           [:h2 "Start shipping faster, build for free using CircleCI today."]
           [:p.subheader "You have a product to focus on, let CircleCI handle your continuous integration and deployment."]
           (common/sign-up-cta owner "changelog")
           ]])))))

