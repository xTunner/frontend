(ns frontend.components.pages.user-settings
  (:require [frontend.components.account :as old-components]
            [frontend.components.common :as common]
            [frontend.components.templates.main :as main-template]
            [frontend.state :as state]
            [frontend.utils.seq :refer [select-in]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (component
        (let [subpage (get-in app state/navigation-subpage-path)
              coms {:notifications old-components/notifications
                    :heroku old-components/heroku-key
                    :api old-components/api-tokens
                    :plans old-components/plans
                    :beta old-components/beta-program}
              subpage-com (get coms subpage)]
          (om/build
           main-template/template
           {:app app
            :show-aside-menu? true
            :main-content
            (element :main-content
              (html
               [:div
                (om/build common/flashes (get-in app state/error-message-path))
                (om/build subpage-com (select-in app [state/general-message-path state/user-path state/projects-path state/web-notifications-enabled-path]))]))}))))))
