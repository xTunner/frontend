(ns frontend.components.templates.main
  "Provides a template for the root of a normal page."
  (:require [frontend.components.aside :as aside]
            [frontend.components.header :as header]
            [frontend.state :as state]
            [frontend.utils.seq :refer [dissoc-in]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn aside [app]
  (case (:navigation-point app)
    :project-settings (om/build aside/project-settings-menu app)
    :org-settings (om/build aside/org-settings-menu app)
    :admin-settings (om/build aside/admin-settings-menu app)
    :account (om/build aside/account-settings-menu app)
    (om/build aside/branch-activity-list app)))

(defn template
  "The template for building a page in the app.

  app              - The entire app state.
  main-content     - A component which forms the main content of the page, which
                     is everything below the header.
  crumbs           - Breadcrumbs to display in the header. Defaults to
                     (get-in app state/crumbs-path), but this is deprecated.
  header-actions   - A component which will be placed on the right in the
                     header. This is used for page-wide actions.
  show-aside-menu? - If true, show the aside menu."
  [{:keys [app main-content crumbs header-actions show-aside-menu?]}]
  (html
   (let [outer? (contains? #{:landing :error} (:navigation-point app))
         logged-in? (get-in app state/user-path)
         ;; simple optimzation for real-time updates when the build is running
         app-without-container-data (dissoc-in app state/container-data-path)]
     ;; Outer gets just a plain div here.
     [(if outer? :div :main.app-main)
      (om/build header/header {:app app-without-container-data
                               :crumbs (or crumbs (get-in app state/crumbs-path))
                               :actions header-actions})

      [:div.app-dominant
       (when (and (not outer?) logged-in? show-aside-menu?)
         [:aside.app-aside
          [:nav.aside-left-menu
           (aside app)]])


       [:div.main-body
        main-content]]])))
