(ns frontend.components.pieces.empty-state
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button])
  (:require-macros [frontend.utils :refer [component html]]))

(defn important
  "An Empty State heading should contain (generally) one important term (often a
  single word), which helps the user understand the context of the Empty State."
  [term]
  (component
    (html [:span term])))

(defn empty-state
  "An Empty State appears in place of a list or other content when that content
  does not exist. For instance, on the Projects page, when there are no projects
  to show, we show an Empty State explaining why and how to change that.

  :icon       - An icon which signals what kind of thing is missing. This may
                be a literal icon, which should appear correctly without
                additional style, or some sort of image, which may require
                additional style. The height of the icon should equal the
                font-size it inherits (that is, 1em).

  :heading    - The first, bigger text shown below the icon. The heading
                typically declares why an Empty State is shown rather than any
                actual content (\"Segmentio has no project building on
                CircleCI\"). It should be a single sentence, in sentence case,
                and should not end with a period.

  :subheading - The secondary text below the heading. The subheading typically
                explains what the user can do to add content and therefore make
                the Empty State go away, along with any other info the user may
                need. It should be a single sentence, and it should end with a
                period.

  :action     - (optional) The action (or actions) a user may wish to take to
                make the Empty State go away. This is typically a single,
                primary button, if present. It may also be multiple buttons."
  [{:keys [icon heading subheading action]}]
  (component
    (html
     [:div
      [:.icon icon]
      [:.heading heading]
      [:.subheading subheading]
      (when action
        [:.action action])])))

(dc/do
  (defcard empty-state
    (html
     (empty-state {:icon (html [:i.material-icons "cake"])
                   :heading (html
                             [:span
                              "The "
                              (important "cake")
                              " is a lie"])
                   :subheading "Let's add some."
                   :action (button/button {:primary? true}
                                          "Add Cake")}))))
