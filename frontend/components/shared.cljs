(ns frontend.components.shared
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.stefon :refer (data-uri)]
            [frontend.utils.ajax :as ajax]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

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

(defn contact-form
  "It's not clear how this should fit into the global state, so it's using component-local
   state for now."
  [app owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:email nil
       :name nil
       :message nil
       :notice nil})
    om/IRenderState
    (render-state [_ {:keys [email name message notice loading?]}]
      (let [controls-ch (om/get-shared owner [:comms :controls])
            clear-notice! #(om/set-state! owner [:notice] nil)
            enterprise? (:enterprise? opts)]
        (html
         [:form.contact-us
          [:label {:for "name"} "Full name"]
          [:input {:value name
                   :class (when loading? "disabled")
                   :type "text",
                   :placeholder "Your Name",
                   :name "name"
                   :on-change #(do (clear-notice!) (om/set-state! owner [:name] (.. % -target -value)))}]
          [:label {:for "email"} "Email address"]
          [:input {:value email,
                   :class (when loading? "disabled")
                   :type "email",
                   :placeholder "Your email address",
                   :name "email"
                   :on-change #(do (clear-notice!) (om/set-state! owner [:email] (.. % -target -value)))}]
          [:label {:for "message"} "Message"]
          [:textarea {:value message,
                      :class (when loading? "disabled")
                      :placeholder "Tell us what you're thinking"
                      :on-change #(do (clear-notice!) (om/set-state! owner [:message] (.. % -target -value)))}]
          [:div.notice (when notice
                         [:div {:class (:type notice)}
                          (:message notice)])]
          [:button.btn-primary {:class (when loading? "disabled")
                                :on-click #(do (if-not (and (seq name) (seq email))
                                                 (om/set-state! owner [:notice] {:type "error"
                                                                                 :message "Name, email, and message are all required."})
                                                 (do
                                                   (om/set-state! owner [:loading?] true)
                                                   (go (let [resp (<! (ajax/managed-form-post
                                                                       "/about/contact"
                                                                       :params {:name name
                                                                                :email email
                                                                                :message message
                                                                                :enterprise enterprise?}))]
                                                         (utils/inspect resp)
                                                         (if (= (:status resp) :success)
                                                           (om/update-state! owner (fn [s]
                                                                                     {:name ""
                                                                                      :email ""
                                                                                      :message ""
                                                                                      :loading? false
                                                                                      :notice (:resp resp)}))
                                                           (do
                                                             (om/set-state! owner [:loading?] false)
                                                             (om/set-state! owner [:notice] {:type "error" :message "Sorry! There was an error sending your message."})))))))
                                               false)}
           (if loading? "Sending..." "Send")]])))))
