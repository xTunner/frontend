(ns frontend.components.pieces.page-header
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.crumbs :as crumbs]
            [frontend.components.pieces.button :as button]
            [frontend.utils.devcards :refer [iframe]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn header
  "The page header.

  :crumbs  - The breadcrumbs to display.
  :actions - (optional) A component (or collection of components) which will be
             placed on the right of the header. This is where page-wide actions are
             placed."
  [{:keys [crumbs actions]} owner]
  (reify
    om/IDisplayName (display-name [_] "User Header")
    om/IRender
    (render [_]
      (component
        (html
         [:div
          ;; Avoids a React warning for not giving each `li` a key. Correctly,
          ;; we should render the `li`s here directly, with a `for`, and give
          ;; each one a `:key` here. This gets rid of the warning for now and
          ;; avoids re-architecting the crumbs rendering.
          (apply vector :ol.breadcrumbs (crumbs/crumbs crumbs))
          [:.actions actions]])))))

(dc/do
  (def ^:private crumbs
    [{:type :dashboard}
     {:type :org
      :username "some-org"
      :vcs_type "github"}
     {:type :project
      :username "some-org"
      :project "a-project"
      :vcs_type "github"}
     {:type :project-branch
      :username "some-org"
      :project "a-project"
      :vcs_type "github"
      :branch "a-particular-branch"}
     {:type :build
      :username "some-org"
      :project "a-project"
      :build-num 66908
      :vcs_type "github"}])

  (defcard header-with-no-actions
    (iframe
     {:width "992px"}
     (om/build header {:crumbs crumbs})))

  (defcard header-with-no-actions-narrow
    (iframe
     {:width "991px"}
     (om/build header {:crumbs crumbs})))

  (defcard header-with-actions
    (iframe
     {:width "992px"}
     (om/build header {:crumbs crumbs
                       :actions [(button/button {} "Do Something")
                                 (button/button {:primary? true} "Do Something")]})))

  (defcard header-with-actions-narrow
    (iframe
     {:width "991px"}
     (om/build header {:crumbs crumbs
                       :actions [(button/button {} "Do Something")
                                 (button/button {:primary? true} "Do Something")]}))))
