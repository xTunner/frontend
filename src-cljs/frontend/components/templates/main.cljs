(ns frontend.components.templates.main
  (:require [frontend.components.aside :as aside]
            [frontend.components.footer :as footer]
            [frontend.components.header :as header]
            [frontend.components.inspector :as inspector]
            [frontend.config :as config]
            [frontend.models.feature :as feature]
            [frontend.state :as state]
            [frontend.utils.seq :refer [dissoc-in]]
            [om.core :as om :include-macros true]
            cljs.pprint
            clojure.data)
  (:require-macros [frontend.utils :refer [html]]))

(defn template [{:keys [app main-content header-actions]} owner]
  (reify
    om/IRender
    (render [_]
      (html
       (let [inner? (get-in app state/inner?-path)
             logged-in? (get-in app state/user-path)
             show-inspector? (get-in app state/show-inspector-path)
             show-footer? (not= :signup (:navigation-point app))
             ;; simple optimzation for real-time updates when the build is running
             app-without-container-data (dissoc-in app state/container-data-path)]
         [:main.app-main
          (when show-inspector?
            ;; TODO inspector still needs lots of work. It's slow and it defaults to
            ;;     expanding all datastructures.
            (om/build inspector/inspector app))

          (om/build header/header {:app app-without-container-data
                                   :actions header-actions})

          [:div.app-dominant
           (when (and inner? logged-in?)
             (om/build aside/aside (dissoc app-without-container-data :current-build-data)))


           [:div.main-body
            main-content

            (when (and (not inner?) show-footer? (config/footer-enabled?))
              [:footer.main-foot
               (footer/footer)])]]])))))
