(ns frontend.components.pages.projects
  (:require [om.core :as om :include-macros true]
            [frontend.components.pieces.org-picker :as org-picker])
  (:require-macros [frontend.utils :refer [html]]))

(defn page [data owner]
  (reify
    om/IRender
    (render [_]
      (let [user (:current-user data)
            repos (:repos user)
            settings (:settings data)
            selected-org (get-in settings [:add-projects :selected-org])]
        (html
         [:div (om/build org-picker/picker
                         {:user user
                          :settings settings
                          :repos repos})])))))
