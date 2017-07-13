(ns frontend.components.pages.build.head.context
  (:require [frontend.components.pages.build.head.summary-item :as summary-item]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.popover :as popover]
            [frontend.models.feature :as feature]
            [frontend.routes :as routes]
            [frontend.utils :as utils :refer-macros [component html]]
            [om.core :as om :include-macros true]))

(defn- tooltip [{:keys [settings-url]} owner]
  (reify
    om/IRender
    (render [_]
      (component
       (html
        [:span
         (popover/tooltip
          {:body
           (html
            [:.context-tooltip
              [:p "A Context is a set of resources that can be shared across projects in your organization."]
              (let [href "https://circleci.com/docs/2.0/contexts/"]
                [:p [:a {:href href
                         :target "_blank"
                         :on-click #((om/get-shared owner :track-event) {:event-type :resource-class-docs-clicked
                                                                         :prperties {:href href}})}
                     "Read more in our docs →"]])
              (let [href settings-url]
                [:p [:a {:href href
                         :on-click #((om/get-shared owner :track-event) {:event-type :resource-class-docs-clicked
                                                                         :properties {:href href}})}
                     "See this organization's Contexts →"]])])
           :placement :bottom}
          [:i.fa.fa-question-circle])])))))

(defn summary-item [context-ids vcs-type org-name]
  (when (feature/enabled? :contexts-v1)
    (when-not (empty? context-ids)
      (let [settings-url (routes/v1-org-settings-path
                          {:vcs_type vcs-type
                           :org org-name
                           :_fragment "contexts"})]
       (html
        (summary-item/summary-item
         [:span "Context:"]
         [:span
          [:a {:href settings-url}
           (first context-ids)]]
         (om/build tooltip {:settings-url settings-url})))))))
