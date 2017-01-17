(ns frontend.components.pages.user-settings.heroku
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.async :refer [raise!]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.form :as form]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- api-key-card
  [{:keys [project-page? heroku-api-key heroku-api-key-input on-change-key-input submit-form!]}]
  (card/titled
   {:title "Heroku API Key"}
   [:div
    [:p
     "Add your " [:a {:href "https://dashboard.heroku.com/account"} "Heroku API Key"]
     " to set up deployment with Heroku."
     [:br]
     ;; Don't tell them to go to the project page if they're already there
     (when-not project-page?
       "You'll also need to set yourself as the Heroku deploy user from your project's settings page.")]
    (when heroku-api-key
      [:p "Your Heroku Key is currently " heroku-api-key])
    (form/form
     {}
     (om/build form/text-field {:label "Heroku Key"
                                :value heroku-api-key-input
                                :on-change on-change-key-input})
     (button/managed-button
      {:loading-text "Saving..."
       :failed-text "Failed to save key"
       :success-text "Saved"
       :kind :primary
       :on-click submit-form!}
      "Save Heroku Key"))]))

(defn subpage [app owner opts]
  (reify
    om/IRenderState
    (render-state [_ _]
      (let [heroku-api-key (get-in app (conj state/user-path :heroku_api_key))
            heroku-api-key-input (get-in app (conj state/user-path :heroku-api-key-input))
            submit-form! #(raise! owner [:heroku-key-add-attempted {:heroku_api_key heroku-api-key-input}])
            project-page? (:project-page? opts)]
        (html
         [:div.account-settings-subpage
          [:legend "Heroku Settings"]
          (api-key-card {:project-page? project-page?
                         :heroku-api-key heroku-api-key
                         :heroku-api-key-input heroku-api-key-input
                         :on-change-key-input #(utils/edit-input owner (conj state/user-path :heroku-api-key-input) %)
                         :submit-form! submit-form!})])))))

(dc/do
  (defcard api-key-card-with-no-key-set
    "Card as displayed when no key is set yet. Here, the user has just entered a new key
    and not yet saved it."
    (api-key-card {:project-page? false
                   :heroku-api-key nil
                   :heroku-api-key-input "75d775d775d775d775d775d775d775d7"
                   :submit-form! #(.preventDefault %)})
    {}
    {:classname "background-gray"})

  (defcard api-key-card-with-key-set
    "Card as displayed when a key is already set. Here, the user has just entered a new
    key and not yet saved it."
    (api-key-card {:project-page? false
                   :heroku-api-key "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx75d7"
                   :heroku-api-key-input "75d775d775d775d775d775d775d775d7"
                   :submit-form! #(.preventDefault %)})
    {}
    {:classname "background-gray"}))
