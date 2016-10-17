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

(defn icon
  "An icon button. content should be an icon.

  :label     - An imperative verb which describes the button. This will be used
               as the ARIA label text and as tooltip text.
  :on-click  - A function called when the button is clicked.
  :disabled? - If true, the button is disabled. (default: false)"
  [{:keys [label on-click disabled?]} content]
  (assert label "For usability, an icon button must provide a textual label (as :label).")
  (component
    (html
     [:button {:title label
               :aria-label label
               :disabled disabled?
               :on-click on-click}
      content])))

(defn link
  "A link styled as a button.

  :data-external - For links that shouldn't render in place. To be used with
                   frontend.utils.html/open-ext.
  :href          - The link target.
  :on-click      - A function called when the link is clicked.
  :kind          - The kind of button. One of #{:primary :secondary :danger :flat}.
                   (default: :secondary)"
  [{:keys [href on-click kind size data-external]
    :or {kind :secondary size :full}}
   content]
  (component
    (html
     [:a.exception
      {:class (remove nil? [(name kind)
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
  (defn button-display [columns]
    (html
     [:div {:style {:display "flex"}}
      (for [column columns]
        [:div.primary {:style {:margin-right "2em"}}
         (for [button column]
           [:div {:style {:margin-bottom "1em"}}
            button])])]))

  (defcard buttons
    "A **button** represents an action a user can take. A button's label should
    be an action—that is, an imperative verb. Clicking the button initiates that
    action.


    ## Kinds

    A **Primary** button is the main action in a given context. Submit actions,
    save actions, and enable actions would all use a Primary button.

    The exception is the **Danger** button, which is used for destructive
    actions.

    The **Secondary** button is used for other actions.

    The **Flat** button is reserved for non-action actions, mainly Cancel
    buttons. A Flat button is appropriate for backing out of a workflow.


    ## Sizes

    The **Full** size button is the default. A **Medium** size is available to
    use in table rows, card headers, and anywhere vertical space is at a
    premium."

    (button-display
     [;; Primary buttons
      [(button {:kind :primary
                :on-click #(js/alert "Clicked!")}
               "Primary")
       (button {:disabled? true
                :kind :primary
                :on-click #(js/alert "Clicked!")}
               "Primary Disabled")
       (button {:kind :primary
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Primary Medium")]

      ;; Secondary buttons
      [(button {:on-click #(js/alert "Clicked!")}
               "Secondary")
       (button {:disabled? true
                :on-click #(js/alert "Clicked!")}
               "Secondary Disabled")
       (button {:size :medium
                :on-click #(js/alert "Clicked!")}
               "Secondary Medium")]

      ;; Danger buttons
      [(button {:kind :danger
                :on-click #(js/alert "Clicked!")}
               "Danger")
       (button {:disabled? true
                :kind :danger
                :on-click #(js/alert "Clicked!")}
               "Danger Disabled")
       (button {:kind :danger
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Danger Medium")]

      ;; Flat buttons
      [(button {:kind :flat
                :on-click #(js/alert "Clicked!")}
               "Flat")
       (button {:disabled? true
                :kind :flat
                :on-click #(js/alert "Clicked!")}
               "Flat Disabled")
       (button {:kind :flat
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Flat Medium")]]))

  (defcard icon-buttons
    "When horizontal space is at a premium, use an **icon button**. These square
    buttons display only a single icon. Like a normal button, an icon button
    requires an imperative verb as its label, but the label is only displayed as
    tooltip text and given to screen readers as the ARIA label.

    Icon buttons are only styled as secondary buttons, and should only be used
    where a secondary button would be appropriate."

    (button-display
     [[(icon {:label "Icon"
              :on-click #(js/alert "Clicked!")}
             [:i.octicon.octicon-repo-forked])]
      [(icon {:label "Icon Disabled"
              :disabled? true
              :on-click #(js/alert "Clicked!")}
             [:i.octicon.octicon-repo-forked])]]))

  (defcard link-buttons
    "A **link-button** looks like a button, but is actually a link.

    A link-button's label, like an ordinary button's, should be an action. Like
    a normal button, clicking it initiates that action. Clicking a link-button
    in particular \"initiates\" the action by navigating to a place in the app
    where the user can continue the action. For instance, \"Add Projects\" is a
    link, because it navigates to the Add Projects page, but it is a link-button
    in particular because it takes the user there to perform the \"Add
    Projects\" action.

    Viewing more information is not an action. \"Build #5\" would not be an
    appropriate label for a link-button; neither would \"View Build #5\".
    Instead, \"Build #5\" should be a normal link."

    (button-display
     [;; Primary link buttons
      [(link {:kind :primary
              :href "#"}
             "Primary Link")
       (link {:kind :primary
              :href "#"
              :size :medium}
             "Medium Primary Link")]

      ;; Secondary link buttons
      [(link {:href "#"}
             "Secondary Link")
       (link {:href "#"
              :size :medium}
             "Medium Secondary Link")]

      ;; Danger link buttons
      [(link {:kind :danger
              :href "#"}
             "Danger Link")
       (link {:kind :danger
              :href "#"
              :size :medium}
             "Medium Danger Link")]

      ;; Flat buttons
      [(link {:kind :flat
              :href "#"}
             "Flat Link")
       (link {:kind :flat
              :href "#"
              :size :medium}
             "Medium Flat Link")]])))
