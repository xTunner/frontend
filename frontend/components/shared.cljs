(ns frontend.components.shared
  (:require [frontend.stefon :refer (data-uri)]
            [frontend.utils :as utils :include-macros true]))

(defn customers-trust [& {:keys [company-size]
                          :or {company-size "big-company"}}]
  [:div.customers-trust.row
   [:h4 [:span "Trusted By"]]
   [:div {:class company-size}
    [:img {:title "Salesforce" :src (data-uri "/img/logos/salesforce.png")}]]
   [:div {:class company-size}
    [:img {:title "Samsung" :src (data-uri "/img/logos/samsung.png")}]]
   [:div {:class company-size}
    [:img {:title "Kickstarter" :src (data-uri "/img/logos/kickstarter.png")}]]
   [:div {:class company-size}
    [:img {:title "Cisco", :src (data-uri "/img/logos/cisco.png")}]]
   [:div {:class company-size}
    [:img {:title "Shopify" :src (data-uri "/img/logos/shopify.png")}]]
   [:span.stretch]])
