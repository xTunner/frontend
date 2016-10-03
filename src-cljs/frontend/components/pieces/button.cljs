(ns frontend.components.pieces.button
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.forms :as forms])
  (:require-macros [frontend.utils :refer [component html]]))

(defn button
  "A standard button.

  :on-click  - A function called when the button is clicked.
  :kind      - The kind of button. One of #{:primary :secondary :dangerous}.
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
  :kind          - The kind of button. One of #{:primary :secondary :dangerous}.
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
  :kind         - The kind of button. One of #{:primary :secondary :dangerous}.
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
  (defcard full-buttons
    "These are our buttons in their normal, `:full` size (the default)."
    (html
     [:div
      [:div
       (button {:kind :primary
                :on-click #(js/alert "Clicked!")}
               "Primary Button")
       (button {:on-click #(js/alert "Clicked!")}
               "Secondary Button")
       (button {:kind :dangerous
                :on-click #(js/alert "Clicked!")}
               "Dangerous Button")]
      [:div
       (button {:disabled? true
                :kind :primary
                :on-click #(js/alert "Clicked!")}
               "Primary Disabled")
       (button {:disabled? true
                :on-click #(js/alert "Clicked!")}
               "Secondary Disabled")
       (button {:disabled? true
                :kind :dangerous
                :on-click #(js/alert "Clicked!")}
               "Dangerous Disabled")]]))

  (defcard medium-buttons
    "These are our buttons in `:medium` size, used in table rows and anywhere
    vertical space is at a premium."
    (html
     [:div
      [:div
       (button {:kind :primary
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Primary Button")
       (button {:size :medium
                :on-click #(js/alert "Clicked!")}
               "Secondary Button")
       (button {:kind :dangerous
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Dangerous Disabled")]
      [:div
       (button {:disabled? true
                :kind :primary
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Primary Disabled")
       (button {:disabled? true
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Secondary Disabled")
       (button {:disabled? true
                :kind :dangerous
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Dangerous Disabled")]]))

  (defcard full-link-buttons
    "These are our link buttons, when you need to style a link as a button."
    (html
     [:div
      [:div
       (link {:kind :primary
              :href "#"}
             "Primary Link")
       (link {:href "#"}
             "Secondary Link")
       (link {:kind :dangerous
              :href "#"}
             "Dangerous Link")]]))

  (defcard medium-link-buttons
    "These are `:medium` size link buttons."
    (html
     [:div
      [:div
       (link {:kind :primary
              :href "#"
              :size :medium}
             "Medium Primary Link")
       (link {:href "#"
              :size :medium}
             "Medium Secondary Link")
       (link {:kind :dangerous
              :href "#"
              :size :medium}
             "Medium Dangerous Link")]])))
