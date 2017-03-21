(ns frontend.components.pieces.empty-state
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.icon :as icon]
            [frontend.utils :refer-macros [component html]]))

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

(defn avatar-icons
  "Displays up to three avatars, overlapped. Suitable to be used as the :icon of
  an empty-state.

  avatar-urls - A collection of up to three avatar image URLs."
  [avatar-urls]
  {:pre [(>= 3 (count avatar-urls))]}
  (component
    (html
     [:div
      (for [avatar-url avatar-urls]
        [:img {:src avatar-url}])])))

(dc/do
  (defcard empty-state
    (empty-state {:icon (icon/project)
                  :heading (html
                            [:span
                             (important "bakry")
                             " has no projects building on CircleCI"])
                  :subheading "Let's fix that by adding a new project."
                  :action (button/button {:kind :primary} "Add Project")}))

  (defcard empty-state-with-avatars
    ;; Images created with `convert -size 1x1 canvas:red gif:- | base64` (then blue and green).
    (empty-state {:icon (avatar-icons
                         ["data:image/gif;base64,R0lGODlhAQABAPAAAP8AAAAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="
                          "data:image/gif;base64,R0lGODlhAQABAPAAAACAAAAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="
                          "data:image/gif;base64,R0lGODlhAQABAPAAAAAA/wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="])
                  :heading (html
                            [:span
                             "Get started by selecting your "
                             (important "favorite color")])
                  :subheading "Select a color to learn more about it."})))
