(ns frontend.components.pieces.button
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.forms :as forms])
  (:require-macros [frontend.utils :refer [component html]]))

(defn button
  "A standard button.

  :on-click  - A function called when the button is clicked.
  :kind      - The kind of button. One of #{:primary :secondary :danger :flat}.
               (default: :secondary)
  :disabled? - If true, the button is disabled. (default: false)
  :size      - The size of the button. One of #{:full :medium}.
               (default: :full)"
  [{:keys [on-click kind disabled? size]
    :or {kind :secondary size :full}}
   content]
  (component
    (html
     [:button {:class (remove nil? [(name kind)
                                    (case size
                                      :full nil
                                      :medium "medium")])
               :disabled disabled?
               :on-click on-click}
      content])))

(defn link
  "A link styled as a button.

  :class         - Additional CSS classes to be applied to the button.
  :data-external - For links that shouldn't render in place. To be used with
                   frontend.utils.html/open-ext.
  :href          - The link target.
  :on-click      - A function called when the link is clicked.
  :kind          - The kind of button. One of #{:primary :secondary :danger :flat}.
                   (default: :secondary)"
  [{:keys [class href on-click kind size data-external]
    :or {kind :secondary size :full}}
   content]
  (component
    (html
     [:a.exception
      {:class (remove nil? [class
                            (name kind)
                            (case size
                              :full nil
                              :medium "medium")])
       :data-external data-external
       :href href
       :on-click on-click}
      content])))

(defn managed-button
  "A managed button.

  :on-click     - A function called when the button is clicked.
  :kind         - The kind of button. One of #{:primary :secondary :danger :flat}.
                  (default: :secondary)
  :disabled?    - If true, the button is disabled. (default: false)
  :size         - The size of the button. One of #{:full :medium}.
                  (default: :full)
  :loading-text - Text to display indicating that the button action is in
                  progress. (default: \"...\")
  :success-text - Text to display indicating that the button action was
                  successful. (default: \"Saved\")
  :failed-text  - Text to display indicating that the button action failed.
                  (default: \"Failed\")"
  [{:keys [kind disabled? size failed-text success-text loading-text on-click]
    :or {kind :secondary size :full disabled? false}}
   content]
  (forms/managed-button
   ;; Normally, manually adding :data-component is not recommended. We
   ;; make an exception here because `forms/managed-button` takes
   ;; hiccup as an argument instead of an element.
   [:button {:data-component `managed-button
             :data-failed-text failed-text
             :data-success-text success-text
             :data-loading-text loading-text
             :disabled disabled?
             :on-click on-click
             :class (remove nil? [(name kind)
                                  (case size
                                    :full nil
                                    :medium "medium")])}
    content]))


(dc/do
  (defcard buttons
    "These are our buttons in their normal, `:full` size (the default) and in
    `:medium` size to be used in table rows and anywhere vertical space is at a premium."

    (html
     [:div {:style {:display "flex"}}

      [:div.primary {:style {:margin-right "2em"}}
       [:div {:style {:margin-bottom "1em"}}
        (button {:kind :primary
                 :on-click #(js/alert "Clicked!")}
                "Primary")]
       [:div {:style {:margin-bottom "1em"}}
        (button {:disabled? true
                 :kind :primary
                 :on-click #(js/alert "Clicked!")}
                "Primary Disabled")]
       [:div {:style {:margin-bottom "1em"}}
        (button {:kind :primary
                 :size :medium
                 :on-click #(js/alert "Clicked!")}
                "Primary Medium")]]

      [:div.secondary {:style {:margin-right "2em"}}
       [:div {:style {:margin-bottom "1em"}}
        (button {:on-click #(js/alert "Clicked!")}
                "Secondary")]
       [:div {:style {:margin-bottom "1em"}}
        (button {:disabled? true
                 :on-click #(js/alert "Clicked!")}
                "Secondary Disabled")]
       [:div {:style {:margin-bottom "1em"}}
        (button {:size :medium
                 :on-click #(js/alert "Clicked!")}
                "Secondary Medium")]]

      [:div.danger {:style {:margin-right "2em"}}
       [:div {:style {:margin-bottom "1em"}}
        (button {:kind :danger
                 :on-click #(js/alert "Clicked!")}
                "Danger")]
       [:div {:style {:margin-bottom "1em"}}
        (button {:disabled? true
                 :kind :danger
                 :on-click #(js/alert "Clicked!")}
                "Danger Disabled")]
       [:div {:style {:margin-bottom "1em"}}
        (button {:kind :danger
                 :size :medium
                 :on-click #(js/alert "Clicked!")}
                "Danger Medium")]]
      [:div.flat {:style {:margin-right "2em"}}
       [:div {:style {:margin-bottom "1em"}}
        (button {:kind :flat
                 :on-click #(js/alert "Clicked!")}
                "Flat")]
       [:div {:style {:margin-bottom "1em"}}
        (button {:disabled? true
                 :kind :flat
                 :on-click #(js/alert "Clicked!")}
                "Flat Disabled")]
       [:div {:style {:margin-bottom "1em"}}
        (button {:kind :flat
                 :size :medium
                 :on-click #(js/alert "Clicked!")}
                "Flat Medium")]]]))

  (defcard link-buttons
    "These are our link buttons, when you need to style a link as a button.
    There are `:medium` size link buttons to be used in table rows and
    anywhere vertical space is at a premium. <br><br>A link-button's label,
    like an ordinary button's, should be an action—that is, an imperative
    verb. Like a normal button, clicking it initiates that action. Clicking
    a link-button in particular \"initiates\" the action by navigating to a
    place in the app where the user can continue the action. For instance,
    \"Add Projects\" is a link, because it navigates to the Add Projects page,
    but it is a link-button in particular because it takes the user there to
    perform the \"Add Projects\" action. <br><br>Viewing more information is
    not an action. \"Build #5\" would not be an appropriate label for a
    link-button; neither would \"View Build #5\". Instead, \"Build #5\"
    should be a normal link."

    (html
     [:div {:style {:display "flex"}}

      [:div.primary {:style {:margin-right "2em"}}
       [:div {:style {:margin-bottom "1em"}}
        (link {:kind :primary
              :href "#"}
             "Primary Link")]
       [:div {:style {:margin-bottom "1em"}}
        (link {:kind :primary
              :href "#"
              :size :medium}
             "Medium Primary Link")]]

      [:div.secondary {:style {:margin-right "2em"}}
       [:div {:style {:margin-bottom "1em"}}
        (link {:href "#"}
             "Secondary Link")]
       [:div {:style {:margin-bottom "1em"}}
        (link {:href "#"
              :size :medium}
             "Medium Secondary Link")]]

      [:div.danger {:style {:margin-right "2em"}}
       [:div {:style {:margin-bottom "1em"}}
        (link {:kind :danger
              :href "#"}
             "Danger Link")]
       [:div {:style {:margin-bottom "1em"}}
        (link {:kind :danger
              :href "#"
              :size :medium}
             "Medium Danger Link")]]
      [:div.flat {:style {:margin-right "2em"}}
       [:div {:style {:margin-bottom "1em"}}
        (link {:kind :flat
              :href "#"}
             "Flat Link")]
       [:div {:style {:margin-bottom "1em"}}
        (link {:kind :flat
              :href "#"
              :size :medium}
             "Medium Flat Link")]]])))
