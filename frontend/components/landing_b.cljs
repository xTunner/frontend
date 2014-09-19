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

(def circle-logo
  [:svg {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 393 100" :enableBackground "new 0 0 393 100"}
   [:circle {:cx "48.5" :cy "50" :r "11.9"}]
   [:path {:d "M48.5,39.3c5.9,0,10.7,4.8,10.7,10.7s-4.8,10.7-10.7,10.7S37.8,55.9,37.8,50S42.6,39.3,48.5,39.3z M48.5,5 C27.6,5,9.9,19.3,5,38.8c-0.1,0.2-0.1,0.4-0.1,0.5c0,1.2,1,2.2,2.2,2.2h18.2c0.9,0,1.6-0.5,2-1.3v-0.1C31,32,39.1,26.4,48.6,26.4 c13,0,23.5,10.5,23.5,23.6S61.6,73.6,48.5,73.6c-9.4,0-17.6-5.6-21.4-13.7v-0.1c-0.4-0.7-1.1-1.3-2-1.3H7.1c-1.2,0-2.2,1-2.2,2.2 c0,0.2,0,0.4,0.1,0.5C9.9,80.7,27.6,95,48.5,95c24.8,0,45-20.2,45-45S73.4,5,48.5,5z M173.1,18.6c0,2.5-2,4.5-4.5,4.5 c-2.5,0-4.5-2-4.5-4.5s2-4.5,4.5-4.5C171.1,14.1,173.1,16.1,173.1,18.6z M171.9,71.3V27.6h-6.8v43.7c0,0.6,0.5,1.1,1.1,1.1h4.5 C171.5,72.4,171.9,72,171.9,71.3z M381.4,11.8c-3.7,0-6.8,3.1-6.8,6.8s3.1,6.8,6.8,6.8s6.8-3.1,6.8-6.8 C388.1,14.8,385,11.8,381.4,11.8z M386.9,27.6v43.7c0,0.6-0.5,1.1-1.1,1.1h-9c-0.6,0-1.1-0.5-1.1-1.1V27.6H386.9z M201.2,26.4 c-6.8,0.4-12.2,3.6-15.8,8.6v-6.3c0-0.6-0.5-1.1-1.1-1.1h-4.5c-0.6,0-1.1,0.5-1.1,1.1l0,0v42.7c0,0.6,0.5,1.1,1.1,1.1h4.5 c0.6,0,1.1-0.5,1.1-1.1V50c0-8.9,6.9-16.2,15.8-16.8c0.6,0,1.1-0.5,1.1-1.1v-4.5C202.3,27,201.8,26.5,201.2,26.4z M261.2,12.9h-4.5 c-0.6,0-1.1,0.5-1.1,1.1v57.2c0,0.6,0.5,1.1,1.1,1.1h4.5c0.6,0,1.1-0.5,1.1-1.1V14.1C262.3,13.5,261.8,12.9,261.2,12.9z M157.4,59 h-5.2c-0.4,0-0.7,0.2-0.9,0.5c-3.1,4.5-8.1,7.4-13.9,7.4c-9.3,0-16.8-7.6-16.8-16.8s7.6-16.8,16.8-16.8c5.9,0,10.9,3,13.9,7.4 c0.2,0.3,0.5,0.5,0.9,0.5h5.2c0.6,0,1.1-0.5,1.1-1.1c0-0.2-0.1-0.4-0.1-0.5c-3.9-7.6-11.9-13-21.1-13c-13,0-23.5,10.5-23.5,23.6 s10.5,23.6,23.6,23.6c9.2,0,17.2-5.3,21.1-13c0.1-0.2,0.1-0.4,0.1-0.5C158.5,59.5,158,59,157.4,59z M247.7,59h-5.2 c-0.4,0-0.7,0.2-0.9,0.5c-3.1,4.5-8.1,7.4-14,7.4c-9.3,0-16.8-7.6-16.8-16.8s7.6-16.8,16.8-16.8c5.9,0,10.9,3,14,7.4 c0.2,0.3,0.5,0.5,0.9,0.5h5.2c0.6,0,1.1-0.5,1.1-1.1c0-0.2,0-0.4-0.1-0.5c-3.9-7.6-11.9-13-21.1-13c-13,0-23.6,10.5-23.6,23.6 s10.5,23.6,23.6,23.6c9.2,0,17.2-5.3,21.1-13c0.1-0.2,0.1-0.4,0.1-0.5C248.8,59.5,248.3,59,247.7,59z M368.5,55.8 c-0.2-0.1-0.4-0.2-0.5-0.2l0,0h-9.7l0,0c-0.4,0-0.7,0.2-1,0.5c-2.2,3.7-6.1,6.2-10.7,6.2c-6.8,0-12.3-5.5-12.3-12.3 s5.5-12.3,12.3-12.3c4.6,0,8.6,2.5,10.7,6.2c0.2,0.4,0.5,0.5,1,0.5l0,0h9.7l0,0c0.2,0,0.4-0.1,0.5-0.2c0.5-0.3,0.6-0.8,0.5-1.3 c-3-9.5-12-16.5-22.5-16.5C333.5,26.4,323,37,323,50s10.5,23.6,23.6,23.6c10.5,0,19.4-6.9,22.5-16.5C369.1,56.6,368.9,56,368.5,55.8 z M292.6,26.4C279.6,26.4,269,37,269,50s10.5,23.6,23.6,23.6c9.2,0,17.2-5.3,21.1-13c0.1-0.2,0.1-0.4,0.1-0.5c0-0.6-0.5-1.1-1.1-1.1 h-5.2c-0.4,0-0.7,0.2-0.9,0.5c-3.1,4.5-8.1,7.4-13.9,7.4c-8.6,0-15.6-6.4-16.6-14.6H315c0.6,0,1.1-0.5,1.1-1.1c0-0.4,0-0.8,0-1.2 C316.2,37,305.6,26.4,292.6,26.4z M276.4,45.5c2-7.1,8.5-12.3,16.2-12.3c7.7,0,14.2,5.2,16.2,12.3H276.4z"}]])

(def customer-logos
  {:shopify
    [:svg.logo-shopify {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 326 100" :enableBackground "new 0 0 326 100"}
     [:path {:d "M98.3,68.3c2,1,5.5,2.4,8.7,2.4c3,0,4.7-1.7,4.7-3.7c0-2-1.2-3.2-4.3-5c-4-2.4-7-5.7-7-9.8c0-7.5,6.5-12.8,15.7-12.8 c4,0,7.3,0.8,9,1.8l-2.5,7.6c-1.4-0.7-4-1.4-6.6-1.4c-3,0-4.9,1.4-4.9,3.5c0,1.7,1.4,3,4,4.3c4.2,2.4,7.6,5.7,7.6,10.3 c0,8.5-6.9,13.3-16.5,13.2c-4.3-0.2-8.5-1.3-10.5-2.7L98.3,68.3z M124.5,77.8l10.6-55h10.7l-4,21.6l0.2,0.3 c2.8-3.4,6.6-5.6,11.2-5.6c5.7,0,8.7,3.7,8.7,9.7c0,1.8-0.3,4.5-0.8,6.9l-4.2,22h-10.7l4-21.2c0.3-1.4,0.4-3.1,0.4-4.8 c0-2.4-1-3.9-3.4-3.9c-3.4,0-7,4.2-8.5,11.3l-3.6,18.5L124.5,77.8L124.5,77.8z M186,39.2c-12.8,0-21.4,11.5-21.4,24.5 c0,8.3,5.2,14.9,14.8,14.9c12.5,0,21.1-11.2,21.1-24.5C200.5,46.3,196,39.2,186,39.2z M180.9,70.4c-3.7,0-5.2-3.1-5.2-7 c0-6,3.2-16,9-16c3.8,0,5,3.2,5,6.5C189.7,60.3,186.4,70.4,180.9,70.4z M227.8,39.1c-4.2,0-8.5,2.7-11.2,6.3h-0.2l0.5-5.4h-9.4 c-0.4,3.6-1.3,9.7-2.1,14.1l-7.5,39h10.7l2.7-16.2h0.3c1.3,0.9,3.7,1.6,6.3,1.6c12.7,0,20.8-12.9,20.8-25.8 C238.8,45.5,235.6,39.1,227.8,39.1z M217.7,70.5c-1.8,0-3.2-0.5-4.3-1.5l1.8-10c1.3-6.7,4.8-11.1,8.5-11.1c3.2,0,4.2,3.1,4.2,5.9 C227.8,60.7,223.6,70.5,217.7,70.5z M239.2,77.7l7.2-37.8h10.8l-7.3,37.8H239.2z M253.1,35.3c-3,0-5-2.1-5-5.3c0-3.5,2.7-6.2,6-6.2 c3.2,0,5.3,2.2,5.3,5.3C259.3,33.1,256.4,35.3,253.1,35.3L253.1,35.3z M259.1,77.7l5.7-29.7h-4.9l1.5-8.1h4.9l0.3-1.8 c0.8-4.5,2.5-9,6.2-12.1c2.8-2.5,6.6-3.5,10.4-3.5c2.7,0,4.5,0.4,5.8,1l-2.1,8.3c-1-0.3-2-0.5-3.4-0.5c-3.5,0-5.8,3.2-6.3,6.8 l-0.4,1.8h7.5l-1.4,8.1h-7.3l-5.7,29.7H259.1z M297.6,39.9l1.7,17.1c0.4,3.8,0.8,6.3,1,9h0.2c0.8-2.7,1.7-4.9,3.2-9l6.5-17.1h11.1 L308.1,68c-4.7,9.5-9.2,16.6-14,21.2c-3.8,3.5-8.3,5.3-10.4,5.8l-3-9c1.8-0.5,4-1.5,6-3c2.5-1.7,4.5-4,5.8-6.5 c0.3-0.5,0.4-1,0.3-1.9l-6.5-34.7C286.2,39.9,297.6,39.9,297.6,39.9z M83,87l-27,5.9l3.8-77.4c0.3,0,0.4,0.2,0.7,0.3l5.2,5.2l7,0.5 c0,0,0.3,0,0.4,0.2c0.3,0.2,0.3,0.5,0.3,0.5L83,87z M57.5,15.5l-2.7,0.8c-1.2-3.5-2.8-5.9-4.8-7.3c-1.5-1-3.1-1.5-4.9-1.4 c-0.4-0.4-0.7-0.8-1.3-1.3c-2-1.5-4.5-1.8-7.5-0.7c-9,3.2-12.8,14.9-14.2,20.8l-7.9,2.4c0,0-1.8,0.5-2.2,1c-0.3,0.6-0.4,2.2-0.4,2.2 L4.8,83.5l50,9.4l3.8-77.4C58,15.4,57.5,15.5,57.5,15.5z M44.7,19.5L36,22.2c1.2-4.5,3.4-9.2,7.6-11C44.5,13.6,44.7,16.7,44.7,19.5z M37.4,8.5c1.8-0.7,3.2-0.7,4.3,0.2c-5.6,2.5-8.2,9.1-9.3,14.6l-6.9,2.1C27.1,19.8,30.7,11,37.4,8.5z M42.3,46.3 c-0.4-0.2-0.8-0.4-1.4-0.5c-0.5-0.2-1.2-0.4-1.7-0.5C38.4,45,37.7,45,37.1,45c-0.7-0.2-1.4-0.2-2.2,0c-0.7,0-1.4,0.2-2,0.4 c-0.5,0.2-1,0.4-1.4,0.8c-0.4,0.3-0.7,0.7-1,1.3c-0.3,0.4-0.3,1-0.4,1.5c0,0.4,0,0.8,0.2,1.3c0.2,0.4,0.4,0.8,0.7,1.2 c0.3,0.4,0.7,0.8,1.3,1.2c0.5,0.4,1.2,0.8,1.8,1.3c1,0.5,2,1.3,2.8,2.1c1,0.8,1.8,1.7,2.7,2.8c0.8,1,1.4,2.2,1.8,3.5 c0.4,1.3,0.7,2.8,0.5,4.3c-0.2,2.7-0.7,4.9-1.7,6.7c-1,1.8-2.2,3.4-3.8,4.3c-1.5,1-3.2,1.7-5.2,2c-1.8,0.3-4,0.2-5.9-0.3 c-1-0.3-2-0.5-2.8-0.8c-0.8-0.3-1.7-0.7-2.4-1.2c-0.7-0.4-1.4-0.8-2-1.4c-0.5-0.4-1-1-1.4-1.4l2.2-7.6c0.4,0.3,0.8,0.7,1.4,1.2 s1.2,0.8,1.8,1.2c0.7,0.4,1.4,0.7,2.1,1c0.6,0.3,1.5,0.5,2.2,0.5c0.7,0.2,1.3,0.2,1.8,0c0.5-0.2,1-0.3,1.3-0.5 c0.4-0.3,0.7-0.7,0.8-1.2c0.1-0.4,0.3-1,0.3-1.4c0-0.5,0-1-0.2-1.5c-0.2-0.4-0.3-1-0.7-1.4c-0.3-0.4-0.7-1-1.3-1.4 c-0.5-0.4-1.2-1-1.8-1.5c-0.8-0.7-1.7-1.4-2.4-2.1s-1.4-1.5-1.8-2.5c-0.5-0.8-1-1.8-1.3-2.8c-0.3-1-0.4-2.1-0.3-3.4 c0.2-2,0.5-4,1.2-5.7c0.7-1.7,1.7-3.2,3-4.5c1.3-1.4,2.8-2.5,4.5-3.4c1.8-0.8,3.8-1.5,6.2-1.8c1.2-0.2,2.1-0.2,3.1-0.2 s1.8,0,2.7,0.2c0.8,0.2,1.7,0.3,2.4,0.4c0.7,0.2,1.3,0.4,1.8,0.7L42.3,46.3z M48,18.5c0-0.3,0-0.7,0-1c-0.9-2.7-0.6-4.9-1.3-6.9 c0.7,0,1.2,0.3,1.7,0.7c1.5,1.2,2.8,3.4,3.7,5.9L48,18.5z"}]]
  :dropbox
    [:svg.logo-dropbox {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 340 100" :enableBackground "new 0 0 340 100"}
     [:path {:d "M53.2,21.6L24.5,39.4L4.8,23.5L33.2,5L53.2,21.6z M4.8,55.1l28.4,18.5l20-16.6L24.5,39.4L4.8,55.1z M53.2,57.1l19.9,16.6 l28.4-18.5L81.8,39.4L53.2,57.1z M101.6,23.5L73.1,5L53.2,21.6l28.6,17.7L101.6,23.5z M53.2,60.6l-20,16.6l-8.6-5.6v6.3L53.2,95 l28.5-17.1v-6.3l-8.6,5.6L53.2,60.6z M127.8,23.7h-11.4v49.5h12.8c3.9,0,9.3,0.2,13.7-3.2c5.4-4.1,8.2-12.3,8.2-21.8 C151.1,23.6,135.6,23.7,127.8,23.7z M128.9,65.1h-1.7V31.8h1.7c4.4,0,10.5,0.8,10.5,16.2C139.4,64.4,133.3,65.1,128.9,65.1z M164,36.7v8.9c0.9-3.2,2.7-9.5,8.7-9.5c0.7,0,1.3,0.1,1.8,0.3v10c-0.9-0.2-1.3-0.2-1.7-0.2c-7.6,0-7.6,8.5-7.6,11.4v16H155v-37H164 z M191,35.9c-11.1,0-15.1,7.7-15.1,19.3c0,12,4.3,19.3,15,19.3c10.8,0,15.1-7.5,15.1-19.3C206,43.5,202,35.9,191,35.9z M191,67.2 c-3.1,0-4.4-2.4-4.4-12.2c0-9.1,1.1-12.2,4.3-12.2h0.1c3.2,0,4.3,2.7,4.3,12.2C195.4,64.8,194.1,67.2,191,67.2z M234.1,39 c-1.8-1.8-4.4-2.9-6.9-2.9c-5,0-7.3,3.7-8.2,6.1v-5.9h-9.9v49.5h9.9V71c1.8,1.2,3.1,3.2,7.2,3.2c11.8,0,12.6-14.2,12.6-19.5 C238.8,48.5,237.7,42.6,234.1,39z M223.5,67c-4.3,0-4.5-3.2-4.5-8.5v-7c0-3.3,0.1-8.5,4.8-8.5c4,0,4.3,5.2,4.3,11.7 C228.1,62.5,227.7,67,223.5,67z M260,36.1c-4.4,0-6.7,2.4-7.6,4V23.7h-10.8v49.5h6.6c0.8-1.8,1.2-3.2,2-4.6c0.9,1.9,2.6,5.7,8.6,5.7 c2,0,4-0.4,6-1.3c6.8-3.5,7.3-14.6,7.3-18.1C271.9,46,269,36.1,260,36.1z M256.6,67.3c-4.3,0-4.2-4.8-4.2-9v-8.6 c0-1.9-0.1-3.8,1.1-5.2c0.7-1,1.9-1.5,3.2-1.5c3,0,4.5,2.5,4.5,12.2C261.2,64.8,259.9,67.3,256.6,67.3z M289.6,35.9 c-11.1,0-15.1,7.7-15.1,19.3c0,12,4.3,19.3,15,19.3c10.8,0,15.1-7.5,15.1-19.3C304.6,43.5,300.5,35.9,289.6,35.9z M289.6,67.2 c-3.1,0-4.4-2.4-4.4-12.2c0-9.1,1.1-12.2,4.3-12.2h0.1c3.1,0,4.3,2.7,4.3,12.2C293.9,64.8,292.5,67.2,289.6,67.2z M316,36.7l4,8.5 l4.8-8.5h8.9L324.1,53l11.1,20.7h-11.2l-5.4-11.2l-6.2,11.2h-9l11-18.9l-9.5-18.1H316z"}]]
  :square
    [:svg.logo-square {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 379 100" :enableBackground "new 0 0 379 100"}
     [:path {:d "M62.6,59.7c0,1.5-1.3,2.7-2.7,2.7H40.8c-1.5,0-2.7-1.3-2.7-2.7V40.6c0-1.5,1.3-2.7,2.7-2.7h19.1c1.5,0,2.7,1.3,2.7,2.7 C62.6,40.6,62.6,59.7,62.6,59.7z M80.3,5h-60C12.1,5,4.9,11.9,4.9,20.1v60c0,8.3,7.2,14.8,15.4,14.8h60c8.3,0,14.6-6.6,14.6-14.8 v-60C94.9,11.9,88.6,5,80.3,5z M78.7,74c0,2.6-1.9,4.8-4.5,4.8H26.5c-2.7,0-4.5-2.2-4.5-4.8V26.3c0-2.6,1.8-5,4.4-5h47.7 c2.6,0,4.5,2.4,4.5,5L78.7,74L78.7,74z M137,79.7c5,0,9.8-1.7,13.4-4.9c3.7-3.2,5.7-7.6,5.7-12.2c0-10.9-10.1-14.8-20-17 c-9.3-2.2-10.9-5-10.9-10c0-5.4,5.3-10,11.5-10c6.4,0,11.5,4.4,11.5,9.9h5.4c0-4.1-1.8-8.1-5.1-11c-3.2-2.8-7.4-4.3-11.9-4.3 c-9.4,0-16.9,6.9-16.9,15.4c0,4.3,1.3,7.6,3.8,10.1c2.4,2.3,5.9,4,11.3,5.2c13.1,3,15.8,6.8,15.8,11.7c0,6.5-6.1,11.8-13.7,11.8 h-0.1c-4,0-7.6-1.5-10.3-4.1c-2.2-2.2-3.4-5-3.4-7.9h-5.4c0,4.4,1.8,8.5,5,11.8C126.5,77.6,131.6,79.7,137,79.7L137,79.7z M200.2,37.4v7.2h0.3c-3.5-5.4-9.5-8.1-15.9-8.1c-12,0-22,9.2-22,21.4c0,12.3,9.7,21.6,22,21.6c6.4,0,12.4-2.6,15.9-8h-0.3v23.4h5.4 V37.4H200.2z M184.6,74.8c-9.5,0-16.7-7-16.7-16.6c0-9.6,7-16.8,16.7-16.8c9.3,0,16.4,7.7,16.4,16.8 C200.9,67.3,193.8,74.8,184.6,74.8z M296.5,37.4v7.2h0.2c-3.5-5.4-9.4-8.1-15.9-8.1c-12.1,0-22,9.2-22,21.4c0,12.3,9.7,21.6,22,21.6 c6.4,0,12.3-2.6,15.9-8h-0.2v7.2h5.4V37.4H296.5z M280.9,74.8c-9.5,0-16.7-7-16.7-16.6c0-9.6,7-16.8,16.7-16.8 c9.3,0,16.4,7.7,16.4,16.8C297.1,67.3,290.1,74.8,280.9,74.8z M317.8,54.7c0-7.3,4.5-11.9,11.6-12.4v-5.5c-5.3,0.4-9.3,1.7-11.5,6.9 h-0.2v-5.9h-5.2v40.9h5.2L317.8,54.7 M374.1,58.5c0-11.8-9.5-21.9-21.4-21.9c-11.7,0-21.2,9.9-21.2,21.5c0,11.7,9.5,21.4,21.2,21.4 c9,0,17.1-6.2,20.1-14.3h-5.5c-2.2,6.3-8.4,9.5-14.7,9.5c-8,0-15.5-6.8-15.8-14.9h37.2C374,59.9,374.1,59,374.1,58.5z M336.9,55.4 c1.3-8.1,7.7-14,15.9-14s14.7,5.8,15.9,14H336.9z M251.3,78.6v-41h-5.2v21.7c0,4.8-0.6,8.8-4.2,12.3c-2.5,2.4-5.2,3.3-8.7,3.3 c-9.4,0-12.4-6.2-12.4-14.7V37.6h-5.2v22.7c0,4.6,0.6,9.5,3.6,13.2c3.4,4.2,8.8,6.1,14.1,6.1c5.4,0,10.7-2.3,13.1-7.4h0.2v6.4H251.3 z"}]]
  :newrelic
    [:svg.logo-newrelic {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 485 100" :enableBackground "new 0 0 485 100"}
     [:path {:d "M176.9,75.8l-13.3-28c-3.2-6.7-6.5-14.1-7.6-17.6l-0.3,0.3c0.4,5,0.5,11.2,0.6,16.6l0.4,28.9h-9.7V16.2h11.2l14.5,29.2 c2.8,5.5,5.3,12.6,6.1,15.3l0.3-0.3c-0.3-3.1-0.9-11.5-0.9-17L178,16.1h9.4v59.7L176.9,75.8L176.9,75.8z M231,57v-2.4 c0-10.2-1.9-15.5-5.7-19c-3.7-3.3-7.6-4.5-12-4.5c-5.5,0-9.8,1.8-13.3,5.8c-3.8,4.3-5.4,9.4-5.4,17.1c0,13.9,7.7,22.9,20.1,22.9 c5.9,0,11.2-1.9,15.7-5.8l-3.7-5.8c-3.2,2.9-6.8,4.3-10.9,4.3c-8.6,0-10.9-6.4-10.9-12.4V57C204.9,57,231,57,231,57z M213.5,38.6 c4.8,0,7.8,4.1,7.8,11.2h-16.1C205.2,42.6,208.2,38.6,213.5,38.6z M281.5,76h-8.9l-5.3-20.1c-1.4-5.1-2.9-11.9-2.9-11.9h-0.2 c0,0-0.7,4.3-2.9,12.3L256.1,76h-8.9l-11.9-43.2l9.4-1.3l4.8,21.1c1.2,5.4,2.2,11.4,2.2,11.4h0.3c0,0,0.9-5.7,2.5-11.7l5.6-20.2h9.4 l5,19.6c1.8,7.1,2.8,12.4,2.8,12.4h0.3c0,0,1-6.7,2.2-11.7l4.5-20.3h9.8L281.5,76z M352.1,61.6c-3.1-5-7.1-10.8-9.2-11.9 c9.3,0,14.9-7.7,14.9-16.2c0-9.3-6-16.9-19.4-16.9h-18.2V76h9.9V50.1c1.8,0.1,2.7,0.7,3.8,1.8c3.2,3.2,5.9,7.5,10.1,14.8l5.1,9.3 h11.8L352.1,61.6z M330,42.6v-18h5.2c8.8,0,12.1,2.9,12.1,8.9c0,3.1-1,5.3-2.7,6.9c-1.8,1.8-4.6,2.2-9.7,2.2H330z M400.2,57v-2.4 c0-10.2-2.1-15.5-5.9-19c-3.7-3.3-7.7-4.5-12.1-4.5c-5.5,0-9.9,1.8-13.4,5.8c-3.8,4.3-5.5,9.4-5.5,17.1c0,13.9,7.7,22.9,20.1,22.9 c5.9,0,11.1-1.9,15.7-5.8l-3.7-5.8c-3.2,2.9-6.7,4.3-10.7,4.3c-8.5,0-10.6-6.4-10.6-12.4V57C374.1,57,400.2,57,400.2,57z M382.2,38.6c4.8,0,7.8,4.1,7.8,11.2h-16.1C374,42.6,376.9,38.6,382.2,38.6z M417.7,76.7c-9.5,0-9.5-8.6-9.5-12.3V27.3 c0-5.9-0.3-9.1-0.9-12.8l9.7-2.2c0.7,2.7,0.8,6.3,0.8,12v37c0,5.9,0.3,6.8,1,7.8c0.5,0.8,2,1.2,3.1,0.7l1.5,5.8 C421.6,76.5,419.8,76.7,417.7,76.7z M434.2,25.8c-3.4,0-6.1-2.9-6.1-6.3c0-3.5,2.8-6.4,6.3-6.4c3.3,0,6.2,2.8,6.2,6.4 C440.5,22.9,437.7,25.8,434.2,25.8z M429.4,75.8V32.9l9.5-1.7v44.6H429.4z M466.1,76.9c-11.9,0-18.5-8.4-18.5-22.4 c0-15.8,9.5-23.4,19.3-23.4c4.8,0,8.2,1.1,12.1,4.8l-4.8,6.3c-2.6-2.3-4.9-3.3-7.3-3.3c-3.1,0-5.5,1.5-6.9,4.4 c-1.3,2.7-1.8,6.8-1.8,12.2c0,5.9,1,9.7,3,11.9c1.4,1.5,3.4,2.5,5.8,2.5c3.1,0,5.9-1.4,8.8-4.3l4.5,5.8 C476.1,75.1,472,76.9,466.1,76.9z M64.9,21.3c-16.2,0-29.4,13.2-29.4,29.4s13.2,29.4,29.4,29.4s29.4-13.2,29.4-29.4 C94.3,34.5,81.1,21.3,64.9,21.3z M60.1,70.1C49,70.1,40,61.1,40,50s9-20.1,20.1-20.1s20.1,9,20.1,20.1S71.2,70.1,60.1,70.1z M114.6,38.1C110.2,18.7,90.1,5,65.3,5c-4.9,0-9.8,0.5-14.7,1.6c-30,6.5-50.2,31.3-45,55.2C9.9,81.3,30.1,95,54.8,95 c4.9,0,9.8-0.5,14.7-1.6c14.6-3.2,27.2-10.5,35.7-21S117.1,49.8,114.6,38.1z M64.9,82.8c-17.7,0-32.1-14.4-32.1-32 c0-17.7,14.4-32.1,32.1-32.1S97,33.1,97,50.8C96.9,68.5,82.5,82.8,64.9,82.8z"}]]
  :spotify
    [:svg.logo-spotify {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 299 100" :enableBackground "new 0 0 299 100"}
     [:path {:d "M108.9,65c-0.4-0.4-0.3-0.7,0-1.1l4.1-4.8c0.4-0.4,0.6-0.4,1,0c3.6,3.2,6.9,4.9,12.1,4.9c3.3,0,6.7-1.3,6.7-4.6 c0-1.1-0.4-2.1-1-2.7c-4.2-4.2-21.5-2.1-21.5-15.5c0-5.8,4.2-12.3,14.4-12.3c6.2,0,10.9,2.2,14.6,5c0.5,0.5,0.4,0.7,0.2,1.1l-3.6,5 c-0.4,0.5-0.5,0.5-1.3,0c-2.1-1.6-5.7-3.8-10.2-3.8c-3.7,0-6.1,1.7-6.1,4.4c0,8,22.5,2.9,22.5,18.2c0,7.4-5.8,12.5-14.8,12.5 C119.4,71.5,113.8,69.3,108.9,65z M161.3,38.8c-4.9,0-7.7,2.2-9.5,4.6v-3.3c0-0.5-0.5-1.1-0.9-1.1h-6.6c-0.7,0-0.5,0.6-0.5,1.2v39.1 c0,0.5,0,1,0.5,1h6.6c0.5,0,1.1-0.5,1.1-1.1V67.1c1.8,2.2,4.9,4.4,9.5,4.4c7.3,0,14.7-5.4,14.7-16.3C176,43.6,167.9,38.8,161.3,38.8 z M159.5,64.4c-4.9,0-8.3-4.4-8.3-9.2s3.2-9.3,8.3-9.3c4.1,0,8.2,3.2,8.2,9.4C167.7,60.7,164.1,64.4,159.5,64.4z M210,48.7 c-0.8-2-2-3.7-3.4-5.1c-1.4-1.4-3.2-2.6-5.2-3.5c-2.1-0.8-4.2-1.3-6.6-1.3c-2.4,0-4.7,0.4-6.7,1.3c-2.1,0.9-3.8,2.1-5.3,3.5 c-1.5,1.5-2.7,3.2-3.6,5.2c-0.8,2-1.4,4.1-1.4,6.4v0.2c0,2.2,0.4,4.4,1.4,6.4c0.8,2,2.1,3.7,3.5,5.1c1.4,1.4,3.2,2.6,5.3,3.5 c2.1,0.8,4.2,1.3,6.7,1.3c2.4,0,4.7-0.5,6.7-1.3c2.1-0.9,3.8-2.1,5.3-3.5c1.5-1.5,2.5-3.2,3.4-5.2c0.8-2,1.2-4.1,1.2-6.4v-0.1 C211.1,52.9,210.8,50.7,210,48.7z M194.7,64.5c-5.8,0-8.8-5-8.8-9.4c0-5.3,3.7-9.2,8.6-9.2c4.3,0,8.8,3.4,8.8,9.4 C203.4,61.1,199.2,64.5,194.7,64.5z M223.7,60.8V46.5h7.5c0.4,0,0.6-0.2,0.6-0.6v-5.7c0-0.5-0.3-0.6-0.5-0.6h-7.6V32 c0-0.5-0.2-0.7-0.7-0.7h-6.7c-0.5,0-0.6,0.3-0.6,0.6v7.7h-3.3c-0.3,0-0.4,0.2-0.4,0.5v6c0,0.4,0.1,0.5,0.5,0.5h3.2v15.7 c0,5.8,3.1,9.3,9.3,9.3c2.9,0,4.6-0.6,6.4-1.5c0.3-0.2,0.4-0.4,0.4-0.7v-5.4c0-0.7-0.4-0.8-1.1-0.5c-0.6,0.3-2,0.9-3.7,0.9 C224.3,64.2,223.7,62.7,223.7,60.8z M242.4,70.9c0.4,0,0.6-0.2,0.6-0.6V40.2c0-0.4-0.2-0.6-0.6-0.6h-6.8c-0.4,0-0.6,0.3-0.6,0.6 v30.1c0,0.4,0.2,0.6,0.6,0.6H242.4z M239.1,25.7c-2.8,0-5,2.2-5,5s2.2,5,5,5s5-2.2,5-5S241.9,25.7,239.1,25.7z M257.6,70.9 c0.4,0,0.5-0.2,0.5-0.5V46.5h6.5l9.9,23.8c-0.8,1.7-1.7,3-3.7,3c-1.6,0-3-0.7-3.6-1c-0.4-0.3-0.8-0.3-1.1,0.3l-2.1,4.5 c-0.3,0.5-0.5,1.1,0,1.3c2.2,1.1,4.1,1.8,7.4,1.8c7,0,8.7-4.9,10.5-8.9l11.8-30.7c0.3-0.6,0.3-1.1-0.5-1.1h-7 c-0.5,0-0.5,0.2-0.7,0.7L278.3,61l-7.7-20.9c-0.2-0.4-0.4-0.6-0.6-0.6H258v-0.8c0-2.5,0.8-4.1,3.6-4.1c1.4,0,2.8,0.4,3.4,0.5 c0.7,0.3,1.2,0.2,1.2-0.5v-5.8c0-0.4-0.1-0.5-0.5-0.6c-0.9-0.2-3.3-0.8-5.6-0.8c-7.6,0-9.9,4.7-9.9,10.5v1.4h-3.2 c-0.4,0-0.5,0.2-0.5,0.5v5.9l0.5,0.5h3.2v23.8c0,0.4,0.2,0.5,0.5,0.5h6.8V70.9z M50.1,5c-24.8,0-45,20.2-45,45s20.2,45,45,45 s45-20.2,45-45S74.9,5,50.1,5z M68.4,71.2c-0.7,0-1.3-0.3-1.9-0.6c-6.5-4-14.7-6-23.3-6c-4.9,0-9.6,0.6-14.2,1.5 c-0.7,0.2-1.6,0.4-2.2,0.4c-1.7,0-2.9-1.3-2.9-2.8c0-1.9,1.1-2.8,2.4-3.1c5.6-1.3,11.1-2,16.8-2c9.8,0,18.6,2.2,26.2,6.7 c1.1,0.6,1.8,1.3,1.8,3C71.2,70.1,69.8,71.2,68.4,71.2z M73.2,59.4c-1,0-1.6-0.4-2.2-0.8c-7.3-4.3-17.5-7.2-28.6-7.2 c-5.7,0-10.6,0.8-14.7,1.9c-0.9,0.3-1.4,0.5-2.2,0.5c-2,0-3.5-1.6-3.5-3.5s0.9-3.2,2.8-3.8c5-1.3,10.3-2.4,17.7-2.4 c11.8,0,23.1,3,32.1,8.3c1.5,0.9,2.1,2,2.1,3.6C76.7,57.7,75.2,59.4,73.2,59.4z M78.8,45.5c-0.9,0-1.4-0.2-2.3-0.7 c-8.1-4.9-20.8-7.6-33-7.6c-6.1,0-12.3,0.6-18,2.2c-0.6,0.2-1.4,0.5-2.3,0.5c-2.4,0-4.2-1.9-4.2-4.3s1.5-3.8,3.1-4.3 c6.4-1.9,13.6-2.8,21.3-2.8c13.2,0,27.1,2.7,37.3,8.6c1.3,0.8,2.3,2,2.3,4C83.1,43.7,81.2,45.5,78.8,45.5z"}]]})

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
                [:a.logo-circleci {:href "/"}
                 circle-logo]
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
                    (common/ico :slim-arrow-right)]]]]]
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
