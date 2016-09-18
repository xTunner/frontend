(ns frontend.components.pieces.org-picker
  (:require [devcards.core :as dc :refer-macros [defcard-om]]
            [frontend.async :refer [raise!]]
            [frontend.utils.bitbucket :as bb-utils]
            [frontend.utils.github :as gh-utils]
            [om.core :as om :include-macros true]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [component html]]))

(defui Organization
  static om-next/Ident
  (ident [this {:keys [organization/vcs-type organization/name]}]
    [:organization/by-vcs-type-and-name {:organization/vcs-type vcs-type :organization/name name}])
  static om-next/IQuery
  (query [this]
    [:organization/name :organization/vcs-type :organization/avatar-url])
  Object
  (render [this]
    (component
      (html
       (let [{:keys [organization/vcs-type organization/name organization/avatar-url] :as props} (om-next/props this)
             {:keys [selected? href on-click]} (om-next/get-computed props)]
         [:li {:class (when selected? "active")
               :on-click on-click}
          [:img.avatar {:src (gh-utils/make-avatar-url {:avatar_url avatar-url} :size 40)}]
          [:.org-name name]
          (if vcs-type
            [:.vcs-icon
             [:a {:href (str (case vcs-type
                               "github" (gh-utils/http-endpoint)
                               "bitbucket" (bb-utils/http-endpoint))
                             "/" name)
                  :target "_blank"}
              (case vcs-type
                "github" [:i.octicon.octicon-mark-github]
                "bitbucket" [:i.fa.fa-bitbucket])]])])))))

(def organization (om-next/factory Organization))

(defui Picker
  "Shows an org picker: a list of orgs, one of which can be selected.

  :orgs         - The orgs to display.
  :selected-org - The currently selected org.
  :on-org-click - A handler called when an org is clicked. Receives the clicked org."
  Object
  (render [this]
    (component
      (let [{:keys [orgs selected-org org-href on-org-click]} (om-next/props this)]
        (html
         [:ul
          (for [org orgs]
            (let [selected? (= selected-org org)]
              (organization (om-next/computed org {:selected? selected?
                                                   :on-click #(on-org-click org)}))))])))))

(def picker (om-next/factory Picker))

(dc/do
  (defn picker-parent [{:keys [selected-org] :as data} owner]
    (om/component
        (html
         [:div
          (om/build picker {:orgs [{:login "pgibbs"
                                    :org false
                                    :vcs_type "github"}
                                   {:login "GibbonsP"
                                    :org false
                                    :vcs_type "bitbucket"}
                                   {:login "Facebook"
                                    :org true
                                    :vcs_type "github"}
                                   {:login "Initech"
                                    :org true
                                    :vcs_type "bitbucket"}]
                            :selected-org selected-org
                            :on-org-click #(om/update! data :selected-org %)})
          "Selected: " (if selected-org
                         (:login selected-org)
                         "(Nothing)")])))

  (defcard-om picker
    picker-parent
    {:selected-org nil}))
