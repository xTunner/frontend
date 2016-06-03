(ns frontend.components.pieces.org-picker
  (:require [frontend.async :refer [raise!]]
            [frontend.utils.bitbucket :as bb-utils]
            [frontend.utils.github :as gh-utils]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- organization [org selected? on-click]
  (html
   (let [login (:login org)
         vcs-type (:vcs_type org)]
     [:li {:data-component `organization
           :class (when selected? "active")
           :on-click on-click}
      [:img.avatar {:src (gh-utils/make-avatar-url org :size 50)
                    :height 50}]
      [:.org-name login]
      (if vcs-type
        [:.vcs-icon
         [:a {:href (str (case vcs-type
                           "github" (gh-utils/http-endpoint)
                           "bitbucket" (bb-utils/http-endpoint))
                         "/" login)
              :target "_blank"}
          (case vcs-type
            "github" [:i.octicon.octicon-mark-github]
            "bitbucket" [:i.fa.fa-bitbucket])]])])))

(defn picker
  "Shows an org picker: a list of orgs, one of which can be selected.

  :orgs         - The orgs to display.
  :selected-org - The currently selected org.
  :on-org-click - A handler called when an org is clicked. Receives the clicked org."
  [{:keys [orgs selected-org on-org-click]} owner]
  (reify
    om/IDisplayName (display-name [_] "Organization Listing")
    om/IRender
    (render [_]
      (html
       [:ul {:data-component `picker}
        (for [org orgs]
          (let [selected? (= selected-org org)]
            (organization org selected? #(on-org-click org))))]))))
