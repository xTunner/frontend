(ns frontend.components.landing-b
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [dommy.core :as dommy]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.components.crumbs :as crumbs]
            [frontend.components.drawings :as drawings]
            [frontend.components.shared :as shared]
            [frontend.env :as env]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :refer [auth-url]]
            [goog.events]
            [goog.dom]
            [goog.style]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [frontend.utils :refer [defrender]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [dommy.macros :refer [sel1 node]]))

(defn mount-header-logo-scroll [owner]
  (let [logo (om/get-node owner "center-logo")
        cta (om/get-node owner "cta")
        nav-bkg (om/get-node owner "nav-bkg")
        no-cta (om/get-node owner "no-cta")
        nav-no-bkg (om/get-node owner "nav-no-bkg")
        first-fig-animate (om/get-node owner "first-fig-animate")
        second-fig-animate (om/get-node owner "second-fig-animate")
        vh (.-height (goog.style/getSize nav-bkg))
        scroll-callback #(do
                           (om/set-state! owner [:header-logo-visible] (neg? (.-bottom (.getBoundingClientRect logo))))
                           (om/set-state! owner [:header-cta-visible] (neg? (.-bottom (.getBoundingClientRect cta))))
                           (om/set-state! owner [:header-bkg-visible] (< (.-bottom (.getBoundingClientRect nav-bkg)) 70))
                           (om/set-state! owner [:header-cta-invisible] (< (.-top (.getBoundingClientRect no-cta)) vh))
                           (om/set-state! owner [:header-bkg-invisible] (< (.-top (.getBoundingClientRect nav-no-bkg)) 70))
                           (om/set-state! owner [:first-fig-animate] (< (.-bottom (.getBoundingClientRect first-fig-animate)) vh))
                           (om/set-state! owner [:second-fig-animate] (< (.-bottom (.getBoundingClientRect second-fig-animate)) vh))
                           (om/set-state! owner [:header-bkg-scroller] (min (js/Math.abs (.-top (.getBoundingClientRect nav-no-bkg)))
                                                                            (js/Math.abs (- 70 (.-bottom (.getBoundingClientRect nav-bkg)))))))]
    (om/set-state! owner [:browser-resize-key]
                   (goog.events/listen
                    (sel1 (om/get-shared owner [:target]) ".app-main")
                    "scroll"
                    scroll-callback))))

(defn calculate-scrollbar-width
  "Inserts and removes an out-of-view element to let us calculate the width of the scrollbar"
  [owner]
  (let [el (node [:div {:style "width: 100px; height: 100px; overflow: scroll; position: fixed; top: -200px"}])]
    (dommy/append! (om/get-node owner) el)
    (om/set-state! owner [:scrollbar-width] (utils/inspect (- (.-offsetWidth el) (.-clientWidth el))))
    (dommy/remove! el)))

(def customer-logos
  {:shopify
    [:svg.logo-shopify {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 351.6 100" :enableBackground "new 0 0 351.6 100"}
     [:path {:d "M103.9,70.3c2.2,1.1,6.1,2.7,9.7,2.7c3.3,0,5.2-1.9,5.2-4.1s-1.3-3.6-4.8-5.6c-4.5-2.7-7.8-6.3-7.8-10.9 c0-8.3,7.2-14.2,17.5-14.2c4.5,0,8.1,0.9,10,2l-2.8,8.4c-1.6-0.8-4.4-1.6-7.3-1.6c-3.3,0-5.5,1.6-5.5,3.9c0,1.9,1.6,3.3,4.5,4.8 c4.7,2.7,8.4,6.3,8.4,11.4c0,9.5-7.7,14.8-18.3,14.7c-4.8-0.2-9.5-1.4-11.7-3L103.9,70.3z"}]
     [:path {:d "M133.1,80.9l11.8-61.1h11.9l-4.5,24l0.2,0.3c3.1-3.8,7.3-6.2,12.5-6.2c6.3,0,9.7,4.1,9.7,10.8c0,2-0.3,5-0.9,7.7l-4.7,24.4 h-11.9l4.5-23.6c0.3-1.6,0.5-3.5,0.5-5.3c0-2.7-1.1-4.3-3.8-4.3c-3.8,0-7.8,4.7-9.4,12.6L145,80.8L133.1,80.9L133.1,80.9z"}]
     [:path {:d "M201.4,38c-14.2,0-23.8,12.8-23.8,27.2c0,9.2,5.8,16.6,16.4,16.6c13.9,0,23.4-12.5,23.4-27.2C217.5,45.9,212.5,38,201.4,38z M195.7,72.7c-4.1,0-5.8-3.4-5.8-7.8c0-6.7,3.6-17.8,10-17.8c4.2,0,5.6,3.6,5.6,7.2C205.5,61.4,201.9,72.7,195.7,72.7z"}]
     [:path {:d "M247.9,37.9c-4.7,0-9.4,3-12.5,7h-0.2l0.6-6h-10.5c-0.5,4-1.4,10.8-2.3,15.7l-8.3,43.3h11.9l3-18h0.3c1.4,1,4.1,1.8,7,1.8 c14.1,0,23.1-14.3,23.1-28.7C260.1,45,256.6,37.9,247.9,37.9z M236.6,72.8c-2,0-3.6-0.6-4.8-1.7l2-11.1c1.4-7.5,5.3-12.3,9.4-12.3 c3.6,0,4.7,3.4,4.7,6.6C247.9,61.9,243.2,72.8,236.6,72.8z"}]
     [:polygon {:points "260.5,80.8 268.5,38.8 280.5,38.8 272.4,80.8"}]
     [:path {:d "M276,33.7c-3.3,0-5.6-2.3-5.6-5.9c0-3.9,3-6.9,6.7-6.9c3.6,0,5.9,2.5,5.9,5.9C282.9,31.2,279.7,33.7,276,33.7L276,33.7z"}]
     [:path {:d "M282.7,80.8l6.3-33h-5.5l1.7-9h5.5l0.3-2c0.9-5,2.8-10,6.9-13.4c3.1-2.8,7.3-3.9,11.6-3.9c3,0,5,0.5,6.4,1.1l-2.3,9.2 c-1.1-0.3-2.2-0.6-3.8-0.6c-3.9,0-6.4,3.6-7,7.6l-0.5,2h8.3l-1.6,9h-8.1l-6.3,33H282.7z"}]
     [:path {:d "M325.4,38.8l1.9,19c0.5,4.2,0.9,7,1.1,10h0.2c0.9-3,1.9-5.5,3.6-10l7.2-19h12.3L337.1,70c-5.2,10.6-10.2,18.5-15.6,23.6 c-4.2,3.9-9.2,5.9-11.6,6.4l-3.3-10c2-0.6,4.5-1.7,6.7-3.3c2.8-1.9,5-4.5,6.4-7.2c0.3-0.6,0.5-1.1,0.3-2.1l-7.2-38.6H325.4z"}]
     [:path {:d "M87,91.1l-30,6.6l4.2-86c0.3,0,0.5,0.2,0.8,0.3l5.8,5.8l7.8,0.6c0,0,0.3,0,0.5,0.2c0.3,0.2,0.3,0.6,0.3,0.6L87,91.1z"}]
     [:path {:d "M58.6,11.7l-3,0.9c-1.3-3.9-3.1-6.6-5.3-8.1c-1.7-1.1-3.4-1.7-5.5-1.6c-0.5-0.5-0.8-0.9-1.4-1.4c-2.2-1.7-5-2-8.3-0.8 c-10,3.6-14.2,16.6-15.8,23.1l-8.8,2.7c0,0-2,0.6-2.5,1.1C7.7,28.3,7.5,30,7.5,30L0,87.2l55.6,10.5l4.2-86 C59.2,11.5,58.6,11.7,58.6,11.7z M44.4,16.1l-9.7,3c1.3-5,3.8-10.2,8.4-12.2C44.2,9.5,44.4,13,44.4,16.1z M36.3,3.9 c2-0.8,3.6-0.8,4.8,0.2C34.9,6.9,32,14.2,30.8,20.3l-7.7,2.3C24.8,16.4,28.8,6.7,36.3,3.9z M41.7,45.9c-0.5-0.2-0.9-0.5-1.6-0.6 c-0.6-0.2-1.3-0.5-1.9-0.6c-0.8-0.2-1.6-0.3-2.3-0.3c-0.8-0.2-1.6-0.2-2.5,0c-0.8,0-1.6,0.2-2.2,0.5c-0.6,0.2-1.1,0.5-1.6,0.9 c-0.5,0.3-0.8,0.8-1.1,1.4c-0.3,0.5-0.3,1.1-0.5,1.7c0,0.5,0,0.9,0.2,1.4c0.2,0.5,0.5,0.9,0.8,1.3c0.3,0.5,0.8,0.9,1.4,1.3 c0.6,0.5,1.3,0.9,2,1.4c1.1,0.6,2.2,1.4,3.1,2.3c1.1,0.9,2,1.9,3,3.1c0.9,1.1,1.6,2.5,2,3.9c0.5,1.4,0.8,3.1,0.6,4.8 c-0.2,3-0.8,5.5-1.9,7.5s-2.5,3.8-4.2,4.8c-1.7,1.1-3.6,1.9-5.8,2.2c-2,0.3-4.4,0.2-6.6-0.3c-1.1-0.3-2.2-0.6-3.1-0.9 s-1.9-0.8-2.7-1.3s-1.6-0.9-2.2-1.6c-0.6-0.5-1.1-1.1-1.6-1.6l2.5-8.4c0.5,0.3,0.9,0.8,1.6,1.3s1.3,0.9,2,1.3 c0.8,0.5,1.6,0.8,2.3,1.1s1.7,0.6,2.5,0.6c0.8,0.2,1.4,0.2,2,0c0.6-0.2,1.1-0.3,1.4-0.6c0.5-0.3,0.8-0.8,0.9-1.3s0.3-1.1,0.3-1.6 c0-0.6,0-1.1-0.2-1.7c-0.2-0.5-0.3-1.1-0.8-1.6c-0.3-0.5-0.8-1.1-1.4-1.6c-0.6-0.5-1.3-1.1-2-1.7c-0.9-0.8-1.9-1.6-2.7-2.3 s-1.6-1.7-2-2.8c-0.6-0.9-1.1-2-1.4-3.1s-0.5-2.3-0.3-3.8c0.2-2.2,0.6-4.4,1.3-6.3c0.8-1.9,1.9-3.6,3.3-5c1.4-1.6,3.1-2.8,5-3.8 c2-0.9,4.2-1.7,6.9-2c1.3-0.2,2.3-0.2,3.4-0.2s2,0,3,0.2c0.9,0.2,1.9,0.3,2.7,0.5c0.8,0.2,1.4,0.5,2,0.8L41.7,45.9z M48.1,15 c0-0.3,0-0.8,0-1.1c-1-3-0.7-5.5-1.5-7.7c0.8,0,1.3,0.3,1.9,0.8c1.7,1.3,3.1,3.8,4.1,6.6L48.1,15z"}]]
  :dropbox
    [:svg.logo-dropbox {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 367.1 100" :enableBackground "new 0 0 367.1 100"}
     [:polygon {:points "53.8,18.5 21.9,38.2 0,20.6 31.6,0"}]
     [:polygon {:points "0,55.7 31.6,76.3 53.8,57.9 21.9,38.2"}]
     [:polygon {:points "53.8,57.9 75.9,76.3 107.5,55.7 85.6,38.2"}]
     [:polygon {:points "107.5,20.6 75.9,0 53.8,18.5 85.6,38.2"}]
     [:polygon {:points "53.8,61.8 31.6,80.2 22.1,74 22.1,81 53.8,100 85.5,81 85.5,74 76,80.2"}]
     [:path {:d "M136.7,20.8H124v55h14.2c4.3,0,10.3,0.2,15.2-3.6c6-4.6,9.1-13.7,9.1-24.2C162.6,20.7,145.3,20.8,136.7,20.8z M137.9,66.8 H136v-37h1.9c4.9,0,11.7,0.9,11.7,18C149.6,66,142.8,66.8,137.9,66.8z"}]
     [:path {:d "M176.9,35.2v9.9c1-3.5,3-10.5,9.7-10.5c0.8,0,1.4,0.1,2,0.3V46c-1-0.2-1.5-0.2-1.9-0.2c-8.4,0-8.4,9.5-8.4,12.7v17.8h-11.4 V35.2H176.9z"}]
     [:path {:d "M206.9,34.3c-12.3,0-16.8,8.6-16.8,21.4c0,13.3,4.8,21.4,16.7,21.4c12,0,16.8-8.3,16.8-21.4 C223.6,42.8,219.1,34.3,206.9,34.3z M206.9,69.1c-3.4,0-4.9-2.7-4.9-13.6c0-10.1,1.2-13.6,4.8-13.6h0.1c3.5,0,4.8,3,4.8,13.6 C211.8,66.5,210.3,69.1,206.9,69.1z"}]
     [:path {:d "M254.8,37.8c-2-2-4.9-3.2-7.7-3.2c-5.6,0-8.1,4.1-9.1,6.8v-6.5h-11v55h11V73.3c2,1.3,3.5,3.6,8,3.6c13.1,0,14-15.8,14-21.7 C260,48.3,258.8,41.8,254.8,37.8z M243,68.9c-4.8,0-5-3.5-5-9.4v-7.8c0-3.7,0.1-9.5,5.3-9.5c4.4,0,4.8,5.8,4.8,13 C248.1,63.9,247.7,68.9,243,68.9z"}]
     [:path {:d "M283.5,34.6c-4.9,0-7.5,2.7-8.5,4.4V20.8h-12v55h7.3c0.9-2,1.3-3.5,2.2-5.1c1,2.1,2.9,6.3,9.5,6.3c2.2,0,4.4-0.4,6.7-1.5 c7.5-3.9,8.1-16.2,8.1-20.1C296.8,45.5,293.6,34.6,283.5,34.6z M279.8,69.2c-4.8,0-4.7-5.3-4.7-10v-9.5c0-2.1-0.1-4.2,1.2-5.8 c0.8-1.1,2.1-1.7,3.6-1.7c3.3,0,5,2.8,5,13.6C284.9,66.4,283.4,69.2,279.8,69.2z"}]
     [:path {:d "M316.4,34.3c-12.3,0-16.8,8.6-16.8,21.4c0,13.3,4.8,21.4,16.7,21.4c12,0,16.8-8.3,16.8-21.4 C333.1,42.8,328.6,34.3,316.4,34.3z M316.4,69.1c-3.4,0-4.9-2.7-4.9-13.6c0-10.1,1.2-13.6,4.8-13.6h0.1c3.5,0,4.8,3,4.8,13.6 C321.2,66.5,319.7,69.1,316.4,69.1z"}]
     [:polygon {:points "345.8,35.2 350.2,44.7 355.5,35.2 365.4,35.2 354.8,53.3 367.1,76.3 354.6,76.3 348.6,63.9 341.7,76.3 331.7,76.3 343.9,55.3 333.3,35.2"}]]
  :square
    [:svg.logo-square {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 410.2 100" :enableBackground "new 0 0 410.2 100"}
     [:path {:d "M64.1,60.8c0,1.7-1.4,3-3,3H39.9c-1.7,0-3-1.4-3-3V39.6c0-1.7,1.4-3,3-3h21.2c1.7,0,3,1.4,3,3V60.8z"}]
     [:path {:d "M83.8,0H17.1C8,0,0,7.7,0,16.8v66.7C0,92.7,8,100,17.1,100h66.7c9.2,0,16.2-7.3,16.2-16.5V16.8C100,7.7,93,0,83.8,0z M82,76.7c0,2.9-2.1,5.3-5,5.3h-53C21,82,19,79.6,19,76.7v-53c0-2.9,2-5.6,4.9-5.6h53c2.9,0,5,2.7,5,5.6V76.7z"}]
     [:path {:d "M146.8,83c5.6,0,10.9-1.9,14.9-5.5c4.1-3.6,6.3-8.4,6.3-13.6c0-12.1-11.2-16.4-22.2-18.9c-10.3-2.4-12.1-5.6-12.1-11.1 c0-6,5.9-11.1,12.8-11.1c7.1,0,12.8,4.9,12.8,11h6c0-4.6-2-9-5.7-12.2c-3.5-3.1-8.2-4.8-13.2-4.8c-10.4,0-18.8,7.7-18.8,17.1 c0,4.8,1.4,8.4,4.2,11.2c2.7,2.6,6.6,4.4,12.6,5.8C159,54.2,162,58.5,162,63.9c0,7.2-6.8,13.1-15.2,13.1h-0.1 c-4.4,0-8.5-1.7-11.4-4.5c-2.5-2.4-3.8-5.6-3.8-8.8h-6c0,4.9,2,9.5,5.6,13.1C135.1,80.7,140.8,83,146.8,83L146.8,83z"}]
     [:path {:d "M217,36v8h0.3c-3.9-6-10.6-9-17.7-9c-13.3,0-24.4,10.2-24.4,23.8c0,13.7,10.8,24,24.4,24c7.1,0,13.8-2.9,17.7-8.9H217v26h6 V36H217z M199.6,77.6c-10.6,0-18.6-7.8-18.6-18.4c0-10.7,7.8-18.7,18.6-18.7c10.3,0,18.2,8.5,18.2,18.7 C217.8,69.2,209.9,77.6,199.6,77.6z"}]
     [:path {:d "M324,36v8h0.2c-3.9-6-10.5-9-17.7-9c-13.4,0-24.4,10.2-24.4,23.8c0,13.7,10.8,24,24.4,24c7.1,0,13.7-2.9,17.7-8.9H324v8h6 V36H324z M306.6,77.6c-10.6,0-18.6-7.8-18.6-18.4c0-10.7,7.8-18.7,18.6-18.7c10.3,0,18.2,8.5,18.2,18.7 C324.7,69.2,316.9,77.6,306.6,77.6z"}]
     [:path {:d "M347.6,55.2c0-8.1,5-13.2,12.9-13.8v-6.1c-5.9,0.4-10.3,1.9-12.8,7.7h-0.2v-6.6h-5.8v45.4h5.8V55.2"}]
     [:path {:d "M410.2,59.4c0-13.1-10.5-24.3-23.8-24.3c-13,0-23.6,11-23.6,23.9c0,13,10.5,23.8,23.6,23.8c10,0,19-6.9,22.3-15.9h-6.1 c-2.5,7-9.3,10.6-16.3,10.6c-8.9,0-17.2-7.6-17.6-16.6h41.3C410.1,61,410.2,60,410.2,59.4z M368.9,56c1.4-9,8.6-15.5,17.7-15.5 c9.1,0,16.3,6.5,17.7,15.5H368.9z"}]
     [:path {:d "M273.8,81.8V36.2H268v24.1c0,5.3-0.7,9.8-4.7,13.7c-2.8,2.7-5.8,3.7-9.7,3.7c-10.4,0-13.8-6.9-13.8-16.3V36.2h-5.8v25.2 c0,5.1,0.7,10.6,4,14.7c3.8,4.7,9.8,6.8,15.7,6.8c6,0,11.9-2.6,14.6-8.2h0.2v7.1H273.8z"}]]
  :newrelic
    [:svg.logo-newrelic {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 528 100" :enableBackground "new 0 0 528 100"}
     [:path {:d "M191.2,78.7l-14.8-31.1c-3.5-7.4-7.2-15.7-8.4-19.6l-0.3,0.3c0.5,5.6,0.6,12.5,0.7,18.4l0.4,32.1h-10.8V12.4h12.4l16.1,32.4 c3.1,6.1,5.9,14,6.8,17l0.3-0.3c-0.3-3.4-1-12.8-1-18.9l-0.2-30.3h10.4v66.3H191.2z"}]
     [:path {:d "M251.3,57.8v-2.7c0-11.3-2.1-17.2-6.3-21.1c-4.1-3.7-8.5-5-13.3-5c-6.1,0-10.9,2-14.8,6.5c-4.2,4.8-6,10.4-6,19 c0,15.5,8.6,25.4,22.3,25.4c6.5,0,12.4-2.1,17.4-6.4l-4.1-6.4c-3.6,3.2-7.6,4.8-12.1,4.8c-9.5,0-12.1-7.1-12.1-13.8v-0.3H251.3z M231.8,37.3c5.3,0,8.7,4.5,8.7,12.5h-17.9C222.6,41.8,225.9,37.3,231.8,37.3z"}]
     [:path {:d "M307.4,78.9h-9.9l-5.9-22.3c-1.5-5.7-3.2-13.2-3.2-13.2h-0.2c0,0-0.8,4.8-3.2,13.7l-5.8,21.8h-9.9l-13.2-48l10.4-1.4 l5.3,23.5c1.3,6,2.5,12.7,2.5,12.7h0.3c0,0,1-6.3,2.8-13l6.2-22.4H294l5.5,21.8c2,7.9,3.1,13.8,3.1,13.8h0.3c0,0,1.1-7.4,2.4-13 l5-22.6h10.9L307.4,78.9z"}]
     [:path {:d "M385.8,62.9c-3.4-5.6-7.9-12-10.2-13.2c10.3,0,16.5-8.5,16.5-18c0-10.3-6.7-18.8-21.5-18.8h-20.2v66h11V50.1 c2,0.1,3,0.8,4.2,2c3.6,3.6,6.6,8.3,11.2,16.4l5.7,10.3h13.1L385.8,62.9z M361.3,41.8v-20h5.8c9.8,0,13.4,3.2,13.4,9.9 c0,3.4-1.1,5.9-3,7.7c-2,2-5.1,2.4-10.8,2.4H361.3z"}]
     [:path {:d "M439.3,57.8v-2.7c0-11.3-2.3-17.2-6.5-21.1c-4.1-3.7-8.6-5-13.4-5c-6.1,0-11,2-14.9,6.5c-4.2,4.8-6.1,10.4-6.1,19 c0,15.5,8.6,25.4,22.3,25.4c6.5,0,12.3-2.1,17.4-6.4l-4.1-6.4c-3.6,3.2-7.4,4.8-11.9,4.8c-9.5,0-11.8-7.1-11.8-13.8v-0.3H439.3z M419.3,37.3c5.3,0,8.7,4.5,8.7,12.5h-17.9C410.2,41.8,413.4,37.3,419.3,37.3z"}]
     [:path {:d "M458.7,79.7c-10.6,0-10.6-9.6-10.6-13.7V24.8c0-6.6-0.3-10.1-1-14.2l10.8-2.4c0.8,3,0.9,7,0.9,13.3v41.1 c0,6.5,0.3,7.6,1.1,8.7c0.6,0.9,2.2,1.3,3.4,0.8l1.7,6.5C463.1,79.4,461.1,79.7,458.7,79.7z"}]
     [:path {:d "M477,23.1c-3.8,0-6.8-3.2-6.8-7c0-3.9,3.1-7.1,7-7.1c3.7,0,6.9,3.1,6.9,7.1C484.1,19.9,480.9,23.1,477,23.1z"}]
     [:polygon {:points "471.7,78.7 471.7,31 482.3,29.1 482.3,78.7"}]
     [:path {:d "M512.5,79.9c-13.2,0-20.6-9.3-20.6-24.9c0-17.6,10.5-26,21.4-26c5.3,0,9.1,1.2,13.4,5.3l-5.3,7c-2.9-2.6-5.4-3.7-8.1-3.7 c-3.4,0-6.1,1.7-7.7,4.9c-1.4,3-2,7.5-2,13.5c0,6.6,1.1,10.8,3.3,13.2c1.5,1.7,3.8,2.8,6.4,2.8c3.4,0,6.6-1.6,9.8-4.8l5,6.4 C523.6,77.9,519,79.9,512.5,79.9z"}]
     [:path {:d "M66.7,18.1c-18,0-32.7,14.7-32.7,32.7c0,18,14.7,32.7,32.7,32.7c18,0,32.7-14.7,32.7-32.7C99.4,32.8,84.7,18.1,66.7,18.1z M61.4,72.3c-12.3,0-22.3-10-22.3-22.3c0-12.3,10-22.3,22.3-22.3c12.3,0,22.3,10,22.3,22.3C83.7,62.3,73.7,72.3,61.4,72.3z"}]
     [:path {:d "M121.9,36.8C117.1,15.2,94.7,0,67.2,0c-5.4,0-10.9,0.6-16.3,1.8C17.6,9-4.9,36.6,0.9,63.1C5.6,84.8,28.1,100,55.5,100 c5.4,0,10.9-0.6,16.3-1.8C88,94.7,102,86.5,111.5,74.9C121,63.3,124.7,49.8,121.9,36.8z M66.7,86.5C47,86.5,31,70.5,31,50.9 c0-19.7,16-35.7,35.7-35.7c19.7,0,35.7,16,35.7,35.7C102.3,70.5,86.3,86.5,66.7,86.5z"}]]
  :spotify
    [:svg.logo-spotify {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 321.1 100" :enableBackground "new 0 0 321.1 100"}
     [:path {:d "M115.3,66.7c-0.5-0.4-0.3-0.8,0-1.2l4.5-5.3c0.4-0.4,0.7-0.4,1.1,0c4,3.5,7.7,5.5,13.4,5.5c3.7,0,7.4-1.4,7.4-5.1 c0-1.2-0.4-2.3-1.1-3c-4.7-4.7-23.9-2.3-23.9-17.2c0-6.5,4.7-13.7,16-13.7c6.9,0,12.1,2.4,16.2,5.6c0.6,0.5,0.5,0.8,0.2,1.2l-4,5.6 c-0.4,0.5-0.6,0.5-1.4,0c-2.3-1.8-6.3-4.2-11.3-4.2c-4.1,0-6.8,1.9-6.8,4.9c0,8.9,25,3.2,25,20.2c0,8.2-6.4,13.9-16.5,13.9 C127,73.9,120.8,71.4,115.3,66.7z"}]
     [:path {:d "M173.6,37.6c-5.5,0-8.6,2.5-10.6,5.1v-3.7c0-0.5-0.6-1.2-1-1.2h-7.3c-0.8,0-0.6,0.7-0.6,1.3v43.5c0,0.5,0,1.1,0.5,1.1h7.3 c0.6,0,1.2-0.6,1.2-1.2V69c2,2.4,5.5,4.9,10.6,4.9c8.1,0,16.3-6,16.3-18.1C189.9,42.9,180.9,37.6,173.6,37.6z M171.6,66 c-5.5,0-9.2-4.9-9.2-10.2c0-5.3,3.5-10.3,9.2-10.3c4.6,0,9.1,3.6,9.1,10.4C180.7,61.9,176.7,66,171.6,66z"}]
     [:path {:d "M227.7,48.6c-0.9-2.2-2.2-4.1-3.8-5.7c-1.6-1.6-3.6-2.9-5.8-3.9c-2.3-0.9-4.7-1.4-7.3-1.4c-2.7,0-5.2,0.5-7.4,1.4 c-2.3,1-4.2,2.3-5.9,3.9c-1.7,1.7-3,3.6-4,5.8c-0.9,2.2-1.5,4.6-1.5,7.1V56c0,2.5,0.5,4.9,1.5,7.1c0.9,2.2,2.3,4.1,3.9,5.7 c1.6,1.6,3.6,2.9,5.9,3.9c2.3,0.9,4.7,1.4,7.4,1.4c2.7,0,5.2-0.5,7.4-1.4c2.3-1,4.2-2.3,5.9-3.9c1.7-1.7,2.8-3.6,3.8-5.8 c0.9-2.2,1.3-4.6,1.3-7.1v-0.1C228.9,53.2,228.6,50.8,227.7,48.6z M210.7,66.1c-6.4,0-9.8-5.6-9.8-10.4c0-5.9,4.1-10.2,9.6-10.2 c4.8,0,9.8,3.8,9.8,10.4C220.3,62.3,215.7,66.1,210.7,66.1z"}]
     [:path {:d "M242.9,62V46.1h8.3c0.5,0,0.7-0.2,0.7-0.7v-6.3c0-0.5-0.3-0.7-0.6-0.7h-8.4v-8.4c0-0.6-0.2-0.8-0.8-0.8h-7.4 c-0.6,0-0.7,0.3-0.7,0.7v8.5h-3.7c-0.3,0-0.5,0.2-0.5,0.5v6.7c0,0.4,0.1,0.6,0.6,0.6h3.6v17.4c0,6.5,3.4,10.3,10.3,10.3 c3.2,0,5.1-0.7,7.1-1.7c0.3-0.2,0.5-0.4,0.5-0.8v-6c0-0.8-0.5-0.9-1.2-0.6c-0.7,0.3-2.2,1-4.1,1C243.6,65.8,242.9,64.1,242.9,62z"}]
     [:path {:d "M263.7,73.2c0.5,0,0.7-0.2,0.7-0.7V39.1c0-0.4-0.2-0.7-0.7-0.7h-7.5c-0.5,0-0.7,0.3-0.7,0.7v33.4c0,0.5,0.2,0.7,0.7,0.7 H263.7z"}]
     [:path {:d "M260,23c-3.1,0-5.6,2.5-5.6,5.6c0,3.1,2.5,5.6,5.6,5.6c3.1,0,5.6-2.5,5.6-5.6C265.6,25.5,263.1,23,260,23z"}]
     [:path {:d "M280.5,73.2c0.4,0,0.6-0.2,0.6-0.6V46.1h7.2l11,26.5c-0.9,1.9-1.9,3.3-4.1,3.3c-1.8,0-3.3-0.8-4-1.1 c-0.5-0.3-0.9-0.3-1.2,0.3l-2.3,5c-0.3,0.6-0.6,1.2,0,1.4c2.5,1.2,4.5,2,8.2,2c7.8,0,9.7-5.4,11.7-9.9l13.1-34.1 c0.3-0.7,0.3-1.2-0.5-1.2h-7.8c-0.5,0-0.6,0.2-0.8,0.8l-8.1,23.1L294.9,39c-0.2-0.5-0.4-0.7-0.7-0.7H281v-0.9c0-2.8,0.9-4.6,4-4.6 c1.6,0,3.1,0.4,3.8,0.6c0.8,0.3,1.3,0.2,1.3-0.5v-6.4c0-0.4-0.1-0.6-0.6-0.7c-1-0.2-3.7-0.9-6.2-0.9c-8.4,0-11,5.2-11,11.7v1.6h-3.6 c-0.5,0-0.6,0.2-0.6,0.6v6.6l0.6,0.6h3.6v26.4c0,0.4,0.2,0.6,0.6,0.6H280.5z"}]
     [:path {:d "M50,0C22.4,0,0,22.4,0,50s22.4,50,50,50c27.6,0,50-22.4,50-50S77.6,0,50,0z M70.3,73.6c-0.8,0-1.4-0.3-2.1-0.7 c-7.2-4.4-16.3-6.7-25.9-6.7c-5.4,0-10.7,0.7-15.8,1.7c-0.8,0.2-1.8,0.5-2.5,0.5c-1.9,0-3.2-1.5-3.2-3.1c0-2.1,1.2-3.1,2.7-3.4 c6.2-1.4,12.3-2.2,18.7-2.2c10.9,0,20.7,2.5,29.1,7.5c1.2,0.7,2,1.5,2,3.3C73.4,72.3,71.9,73.6,70.3,73.6z M75.7,60.4 c-1.1,0-1.8-0.4-2.5-0.9c-8.1-4.8-19.4-8-31.8-8c-6.3,0-11.8,0.9-16.3,2.1c-1,0.3-1.5,0.6-2.4,0.6c-2.2,0-3.9-1.8-3.9-3.9 c0-2.1,1-3.6,3.1-4.2c5.6-1.5,11.4-2.7,19.7-2.7c13.1,0,25.7,3.3,35.7,9.2c1.7,1,2.3,2.2,2.3,4C79.6,58.6,77.9,60.4,75.7,60.4z M81.9,45c-1,0-1.6-0.2-2.6-0.8c-9-5.4-23.1-8.4-36.7-8.4c-6.8,0-13.7,0.7-20,2.4c-0.7,0.2-1.6,0.5-2.6,0.5c-2.7,0-4.7-2.1-4.7-4.8 c0-2.7,1.7-4.2,3.5-4.8c7.1-2.1,15.1-3.1,23.7-3.1c14.7,0,30.1,3,41.4,9.6c1.5,0.9,2.6,2.2,2.6,4.5C86.7,43,84.5,45,81.9,45z"}]]})

(def customer-brands
  (array-map
    :shopify {:name "Shopify"
              :logo ""
              :quote "test1a"
              :cite "test1b"
              :cite-title "test1c"
              :cite-avatar ""
              :tools ""
              ;; TODO get position from index rather than specifying manually
              :position 0}
    :dropbox {:name "Dropbox"
              :logo ""
              :quote "test2a"
              :cite "test2b"
              :cite-title "test2c"
              :cite-avatar ""
              :tools ""
              :position 1}
    :square {:name "Square"
             :logo ""
             :quote "test3a"
             :cite "test3b"
             :cite-title "test3c"
             :cite-avatar ""
             :tools ""
             :position 2}
    :newrelic {:name "New Relic"
               :logo ""
               :quote "test4a"
               :cite "test4b"
               :cite-title "test4c"
               :cite-avatar ""
               :tools ""
               :position 3}
    :spotify {:name "Spotify"
              :logo ""
              :quote "test5a"
              :cite "test5b"
              :cite-title "test5c"
              :cite-avatar ""
              :tools ""
              :position 4}))

(defn home [app owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (mount-header-logo-scroll owner)
      (calculate-scrollbar-width owner))
    om/IInitState
    (init-state [_]
      {:header-logo-visible false
       :header-cta-visible false
       :header-bkg-visible false
       :header-cta-invisible false
       :header-bkg-invisible false
       :first-fig-animate false
       :second-fig-animate false
       :scrollbar-width 0
       :scroll-ch (chan (sliding-buffer 1))})
    om/IRenderState
    (render-state [_ state]
      (let [ab-tests (:ab-tests app)
            controls-ch (om/get-shared owner [:comms :controls])
            selected-customer (get-in app state/customer-logo-customer-path :shopify)]
        (html [:div.home.page
               [:nav.home-nav {:style (merge {:width (if (< 0 (:scrollbar-width state))
                                                       (str "calc(100% - " (:scrollbar-width state) "px)")
                                                       "100%")}
                                             (when (> 70 (:header-bkg-scroller state))
                                               {:background-size (str "100% " (:header-bkg-scroller state) "px")}))
                               :class (concat
                                       (when (:header-logo-visible state) ["logo-visible"])
                                       (when (:header-cta-visible state) ["cta-visible"])
                                       (when (:header-bkg-visible state) ["bkg-visible"])
                                       (when (:header-bkg-invisible state) ["bkg-invisible"])
                                       (when (:header-cta-invisible state) ["cta-invisible"]))}
                [:a.promo "What is Continuous Integration?"]
                [:a.login {:href (auth-url)
                           :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                                       :properties {:source "header-log-in"}
                                                                                       :path (auth-url)}])}
                 "Log In"]
                (om/build drawings/logo-circleci app)
                [:a.action {:href (auth-url)
                            :role "button"
                            :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                                        :properties {:source "header-cta"}
                                                                                        :path (auth-url)}])}
                 "Sign Up Free"]]
               [:section.home-prolog {:ref "nav-bkg"}
                [:a.home-action {:href (auth-url)
                                 :role "button"
                                 :ref "cta"
                                 :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                                             :properties {:source "prolog-cta"}
                                                                                             :path (auth-url)}])}
                 "Sign Up Free"]
                [:div.home-cover]
                [:div.home-top-shelf]
                [:div.home-slogans
                 [:h1.slogan.proverb {:title "Your org needs a hero."
                                      :alt   "Let's just authorize first."}
                  "Your org needs a hero."]
                 [:h3.slogan.context {:title "You have a product to focus on, let Circle handle your"
                                      :alt   "Signing up using your GitHub login lets us start really fast."}
                  "You have a product to focus on, let Circle handle your"]
                 [:h3.slogan.context {:title "Continuous Integration & Deployment."
                                      :alt   "Currently, we must request permissions in bulk."}
                  "Continuous Integration & Deployment."]]
                [:div.home-avatars
                 [:div.avatars
                  [:div.avatar-github
                   (common/ico :github)]
                  [:div.avatar-circle {:ref "center-logo"}
                   (common/ico :logo)]]]
                [:div.home-bottom-shelf
                 [:a {:on-click #(put! controls-ch [:home-scroll-one-clicked])}
                  "Learn more"
                  (common/ico :chevron-down)]]]
               [:section.home-purpose {:class (when (:first-fig-animate state) ["animate"])}
                [:div.home-drawings
                 [:figure]
                 [:figure]
                 [:figure
                  (om/build drawings/drawing-dashboard app)]]
                [:div.home-articles
                 [:article {:ref "first-fig-animate"}
                  [:h1
                   "Launches are dead,"
                   [:br]
                   " long live iteration."]
                  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed tempus felis quis dictum mollis. Vivamus non tempor diam. Maecenas sagittis condimentum sapien. Ut sed gravida augue. Proin elementum molestie feugiat. Etiam finibus, neque a consectetur ultrices, tortor ligula blandit mi, ac ornare nisi felis ac dui. Fusce porta vel nunc sed commodo. Praesent bibendum ex hendrerit, bibendum elit et, egestas arcu."]
                  [:p
                   [:a.shopify-link
                    "See how Shopify does it"
                    (common/ico :slim-arrow-right)]]]]
                [:div.home-bottom-shelf
                 [:a {:on-click #(put! controls-ch [:home-scroll-one-clicked])}
                  "Learn more"
                  (common/ico :chevron-down)]]]
               [:section.home-practice
                [:div.practice-tools
                 [:article
                  [:div.practice-tools-high
                   (common/ico :slim-rails)
                   (common/ico :slim-django)
                   (common/ico :slim-node)]
                  [:div.practice-tools-low
                   (common/ico :slim-ruby)
                   (common/ico :slim-python)
                   (common/ico :slim-js)
                   (common/ico :slim-java)
                   (common/ico :slim-php)]]]
                [:div.practice-articles
                 [:article
                  [:h1
                   "Devs rely on us to just work,"
                   [:br]
                   "we support the right tools."]
                  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed tempus felis quis dictum mollis. Vivamus non tempor diam. Maecenas sagittis condimentum sapien. Ut sed gravida augue. Proin elementum molestie feugiat. Etiam finibus, neque a consectetur ultrices, tortor ligula blandit mi, ac ornare nisi felis ac dui. Fusce porta vel nunc sed commodo. Praesent bibendum ex hendrerit, bibendum elit et, egestas arcu."]]]
                [:div.practice-customers
                 [:article
                  [:h5 "Trusted by"]
                  [:div.customers-brands {:class (str "selected-" (get-in customer-brands [selected-customer :position]))}
                   (for [[customer template] customer-brands]
                     [:a.customers-brand {:on-click #(put! controls-ch [:customer-logo-clicked {:customer customer}])}
                      (get customer-logos customer)])]
                  [:div.quote-card
                   [:p (get-in customer-brands [selected-customer :quote])]
                   [:footer
                    [:div.avatar]
                    [:cite
                     (get-in customer-brands [selected-customer :cite])
                     [:br]
                     (get-in customer-brands [selected-customer :cite-title])
                     " at "
                     (get-in customer-brands [selected-customer :name])]]]]]]
               [:section.home-potential {:class (when (:second-fig-animate state) ["animate"])}
                [:div.home-articles
                 [:article {:ref "second-fig-animate"}
                  [:h1
                   "Look under the hood &"
                   [:br]
                   " check the bullet points."]
                  [:div.home-potential-bullets
                   [:ul
                    [:li "Quick & easy setup"]
                    [:li "Lightning fast builds"]
                    [:li "Deep Customization"]
                    [:li "Easy debugging"]]
                   [:ul
                    [:li "Smart notifications"]
                    [:li "Loving support"]
                    [:li "Automatic parallelization"]
                    [:li "Continuous Deployment"]]
                   [:ul
                    [:li "Build artifacts"]
                    [:li "Clean build environments"]
                    [:li "GitHub Integration"]
                    [:li "Open Source Support"]]]]]
                [:div.home-drawings
                 [:figure]
                 [:figure]
                 [:figure
                  (om/build drawings/drawing-build app)]]]
               [:section.home-epilog {:ref "nav-no-bkg"}
                [:a.home-action {:href (auth-url)
                                 :ref "no-cta"
                                 :role "button"
                                 :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                                             :properties {:source "epilog-cta"}
                                                                                             :path (auth-url)}])}
                 "Sign Up Free"]
                [:div.home-cover]
                [:div.home-top-shelf]
                [:div.home-slogans
                 [:h1.slogan.proverb {:title "So, ready to be a hero?"
                                      :alt   "Let's just authorize first."}
                  "So, ready to be a hero?"]
                 [:h3.slogan.context {:title "Next you'll just need to sign in using your GitHub account."
                                      :alt   "Signing up using your GitHub login lets us start really fast."}
                  "Next you'll just need to sign in using your GitHub account."]
                 [:h3.slogan.context {:title "Still not convinced yet? Try taking the full tour."
                                      :alt   "Currently, we must request permissions in bulk."}
                  "Still not convinced yet? Try taking the "
                  [:a {:href "#"} "full tour"]
                  "."]]
                [:div.home-avatars
                 [:div.avatars
                  [:div.avatar-github
                   (common/ico :github)]
                  [:div.avatar-circle
                   (common/ico :logo)]]]
                [:div.home-bottom-shelf
                 [:span.home-footer-bait
                  "About Us"
                  (common/ico :chevron-down)]
                 [:div.home-footer
                  [:div.home-footer-logo
                   (common/ico :logo)]
                  [:nav.home-footer-about
                   [:h5 "CircleCI"]
                   [:a {:href "/tour"} "Tour"]
                   [:a {:href "/about"} "About"]
                   [:a "Support"]
                   [:a {:href "/about#contact"} "Press"]
                   [:a {:href "/jobs"} "Jobs"]
                   [:a {:href "http://blog.circleci.com"} "Blog"]]
                  [:nav.home-footer-product
                   [:h5 "Product"]
                   [:a {:href "/pricing"} "Pricing"]
                   [:a {:href "/docs"} "Documentation"]
                   [:a {:href "/privacy"} "Privacy"]
                   [:a {:href "/security"} "Security"]
                   [:a {:href "/enterprise"} "Enterprise"]
                   [:a {:href "/changelog"} "Changelog"]]
                  [:nav.home-footer-contact
                   [:h5 "Contact"]
                   [:a {:href "https://twitter.com/circleci"} "Twitter"]
                   [:a {:href "mailto:sayhi@circleci.com"} "Email"]
                   [:a {:href ""} "Support Chat"]
                   [:a {:href ""} "Phone"]
                   [:a {:href "https://goo.gl/maps/uhkLn"} "San Francisco, CA"]]]]]])))))
