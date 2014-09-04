(ns frontend.components.drawings
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [put!]]
            [frontend.state :as state]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [frontend.utils :refer [defrender]]))

(defn draw-nav [app owner]
  (reify
    om/IRender
    (render [_]
      (let [ab-tests (:ab-tests app)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html [:g.draw-nav
               [:rect {:height "1000" :width "100"}]
               [:circle {:r "20" :cy "950" :cx "50"}]
               [:circle {:r "20" :cy "350" :cx "50"}]
               [:circle {:r "20" :cy "250" :cx "50"}]
               [:circle {:r "20" :cy "150" :cx "50"}]
               [:path {:d "M50,45.2c2.6,0,4.8,2.1,4.8,4.8s-2.1,4.8-4.8,4.8c-2.6,0-4.8-2.1-4.8-4.8S47.4,45.2,50,45.2z M50,30c-9.3,0-17.1,6.4-19.4,15c0,0.1,0,0.2,0,0.2c0,0.5,0.4,1,1,1h8.1c0.4,0,0.7-0.2,0.9-0.6c0,0,0,0,0,0 c1.7-3.6,5.3-6.1,9.5-6.1c5.8,0,10.5,4.7,10.5,10.5c0,5.8-4.7,10.5-10.5,10.5c-4.2,0-7.8-2.5-9.5-6.1c0,0,0,0,0,0 c-0.2-0.3-0.5-0.6-0.9-0.6h-8.1c-0.5,0-1,0.4-1,1c0,0.1,0,0.2,0,0.2c2.2,8.6,10.1,15,19.4,15c11,0,20-9,20-20S61,30,50,30z"}]])))))

(defn draw-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [ab-tests (:ab-tests app)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html [:g.draw-menu
               [:line {:x1 "350" :y1 "0" :x2 "350" :y2 "1000"}]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 505)"} "whoops"]
                [:polyline {:points "318.6,502.3 321.4,505 329.4,497"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 480)"} "test-notifs"]
                [:polyline {:points "318.6,477.3 321.4,480 329.4,472"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 455)"} "stability-fixes"]
                [:polyline {:points "318.6,452.3 321.4,455 329.4,447"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 430)"} "simpler-gh-user"]
                [:polyline {:points "318.6,427.3 321.4,430 329.4,422"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 330)"} "om-simple-settings"]
                [:polyline {:points "318.6,402.3 321.4,405 329.4,397"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 405)"} "simple-sidebar-fixer"]
                [:polyline {:points "318.6,352.3 321.4,355 329.4,347"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 380)"} "simple-sidebar"]
                [:path {:d "M328,380l-8-8 M328,372l-8,8"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 355)"} "simple-settings"]
                [:polyline {:points "318.6,327.3 321.4,330 329.4,322"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 305)"} "new-invite-modal"]
                [:path {:d "M328,305l-8-8 M328,297l-8,8"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 280)"} "master"]
                [:polyline {:points "318.6,277.3 321.4,280 329.4,272"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 255)"} "design-listing-rewrite"]
                [:polyline {:points "318.6,252.3 321.4,255 329.4,247"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 230)"} "customer-story-styles"]
                [:polyline {:points "318.6,227.3 321.4,230 329.4,222"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 205)"} "build-top-queue-fixes"]
                [:polyline {:points "318.6,202.3 321.4,205 329.4,197"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 180)"} "build-switcher-styles"]
                [:path {:d "M328,180l-8-8 M328,172l-8,8"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 155)"} "brand-new-nav"]
                [:polyline {:points "318.6,152.3 321.4,155 329.4,147"}]]
               [:g
                [:text {:transform "matrix(1 0 0 1 125 130)"} "aside-nav-v2"]
                [:polyline {:points "318.6,127.3 321.4,130 329.4,122"}]]
               [:line {:x1 "100" :y1 "100" :x2 "350" :y2 "100"}]
               [:text {:transform "matrix(1 0 0 1 145.3517 55)"} "Your Branch Activity"]])))))

(defn draw-main [app owner]
  (reify
    om/IRender
    (render [_]
      (let [ab-tests (:ab-tests app)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html [:g.draw-main
               [:g.draw-tr
                [:line {:x1 "350" :y1 "100" :x2 "1600" :y2 "100"}]
                [:text.draw-th {:transform "matrix(1 0 0 1 400 55.0535)"} "Build"]
                [:text.draw-th {:transform "matrix(1 0 0 1 637.1372 55)"} "Author"]
                [:text.draw-th {:transform "matrix(1 0 0 1 896.2832 55)"} "Log"]
                [:text.draw-th {:transform "matrix(1 0 0 1 1331.788 55)"} "Length"]
                [:text.draw-th {:transform "matrix(1 0 0 1 1525.047 55)"} "Status"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "150" :x2 "1600" :y2 "150"}]
                [:path {:d "M1574.5,137.5h-50c-6.9,0-12.5-5.6-12.5-12.5v0c0-6.9,5.6-12.5,12.5-12.5h50 c6.9,0,12.5,5.6,12.5,12.5v0C1587,131.9,1581.4,137.5,1574.5,137.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 130.3062)"} "100273"]
                [:text {:transform "matrix(1 0 0 1 637.1372 130.3062)"} "Paul Biggar"]
                [:text {:transform "matrix(1 0 0 1 896.2832 130.3062)"} "Hide SDS settings behind a feature flag"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 130.3062)"} "02:50"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "200" :x2 "1600" :y2 "200"}]
                [:path {:d "M1574.5,187.5h-50c-6.9,0-12.5-5.6-12.5-12.5v0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5v0 C1587,181.9,1581.4,187.5,1574.5,187.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 180.3062)"} "100236"]
                [:text {:transform "matrix(1 0 0 1 637.1372 180.3062)"} "Allen Rohner"]
                [:text {:transform "matrix(1 0 0 1 896.2832 180.3062)"} "Slightly nicer looking SDS form"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 180.3062)"} "20:34"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "250" :x2 "1600" :y2 "250"}]
                [:path {:d "M1574.5,237.5h-50c-6.9,0-12.5-5.6-12.5-12.5v0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5v0 C1587,231.9,1581.4,237.5,1574.5,237.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 230.3062)"} "100224"]
                [:text {:transform "matrix(1 0 0 1 637.1372 230.3062)"} "David Lowe"]
                [:text {:transform "matrix(1 0 0 1 896.2832 230.3062)"} "Move AWS credentials action to node template"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 230.3062)"} "21:32"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "300" :x2 "1600" :y2 "300"}]
                [:path {:d "M1574.5,287.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,281.9,1581.4,287.5,1574.5,287.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 280.3062)"} "100120"]
                [:text {:transform "matrix(1 0 0 1 637.1372 280.3062)"} "Jenneviere Villegas"]
                [:text {:transform "matrix(1 0 0 1 896.2832 280.3062)"} "AWS credentials need to be in both places"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 280.3062)"} "21:39"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "350" :x2 "1600" :y2 "350"}]
                [:path {:d "M1574.5,337.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,331.9,1581.4,337.5,1574.5,337.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 330.3062)"} "100032"]
                [:text {:transform "matrix(1 0 0 1 637.1372 330.3062)"} "Daniel Woelfel"]
                [:text {:transform "matrix(1 0 0 1 896.2832 330.3062)"} "Fight the good fight against auto-keywordisation"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 330.3062)"} "20:46"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "400" :x2 "1600" :y2 "400"}]
                [:path {:d "M1574.5,387.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,381.9,1581.4,387.5,1574.5,387.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 380.3062)"} "99915"]
                [:text {:transform "matrix(1 0 0 1 637.1372 380.3062)"} "Mahmood Ali"]
                [:text {:transform "matrix(1 0 0 1 896.2832 380.3062)"} "Use correct var for the SDS app name"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 380.3062)"} "21:21"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "450" :x2 "1600" :y2 "450"}]
                [:path {:d "M1574.5,437.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,431.9,1581.4,437.5,1574.5,437.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 430.3062)"} "99901"]
                [:text {:transform "matrix(1 0 0 1 637.1372 430.3062)"} "Gordon Syme"]
                [:text {:transform "matrix(1 0 0 1 896.2832 430.3062)"} "TEMP: some debugging action-log output"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 430.3062)"} "21:54"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "500" :x2 "1600" :y2 "500"}]
                [:circle {:cx "1574.5" :cy "475" :r "12.5"}]
                [:circle {:cx "1524.5" :cy "475" :r "12.5"}]
                [:rect {:x "1524.5" :y "462.5" :width "50" :height "25"}]
                [:text {:transform "matrix(1 0 0 1 400 480.3062)"} "99899"]
                [:text {:transform "matrix(1 0 0 1 637.1372 480.3062)"} "Danny King"]
                [:text {:transform "matrix(1 0 0 1 896.2832 480.3062)"} "remote-spit needs to know /where/ to spit"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 480.3062)"} "21:34"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "550" :x2 "1600" :y2 "550"}]
                [:path {:d "M1574.5,537.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,531.9,1581.4,537.5,1574.5,537.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 530.3062)"} "99895"]
                [:text {:transform "matrix(1 0 0 1 637.1372 530.3062)"} "Nick Gottlieb"]
                [:text {:transform "matrix(1 0 0 1 896.2832 530.3062)"} "Rehash pyenv binaries after pip-installing"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 530.3062)"} "21:06"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "600" :x2 "1600" :y2 "600"}]
                [:path {:d "M1574.5,587.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,581.9,1581.4,587.5,1574.5,587.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 580.3062)"} "99585"]
                [:text {:transform "matrix(1 0 0 1 637.1372 580.3062)"} "Emile Snyder"]
                [:text {:transform "matrix(1 0 0 1 896.2832 580.3062)"} "Unmangle the SDS app name"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 580.3062)"} "22:41"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "650" :x2 "1600" :y2 "650"}]
                [:path {:d "M1574.5,637.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,631.9,1581.4,637.5,1574.5,637.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 630.3062)"} "99557"]
                [:text {:transform "matrix(1 0 0 1 637.1372 630.3062)"} "Tim Dixon"]
                [:text {:transform "matrix(1 0 0 1 896.2832 630.3062)"} "update conversion ID"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 630.3062)"} "21:56"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "700" :x2 "1600" :y2 "700"}]
                [:path {:d "M1574.5,687.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,681.9,1581.4,687.5,1574.5,687.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 680.3062)"} "99542"]
                [:text {:transform "matrix(1 0 0 1 637.1372 680.3062)"} "Ian Duncan"]
                [:text {:transform "matrix(1 0 0 1 896.2832 680.3062)"} "Deployment group is an optional key"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 680.3062)"} "22:22"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "750" :x2 "1600" :y2 "750"}]
                [:path {:d "M1574.5,737.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,731.9,1581.4,737.5,1574.5,737.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 730.3062)"} "99536"]
                [:text {:transform "matrix(1 0 0 1 637.1372 730.3062)"} "Kevin Bell"]
                [:text {:transform "matrix(1 0 0 1 896.2832 730.3062)"} "Add buttons to clear AWS/SDS settings"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 730.3062)"} "22:08"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "800" :x2 "1600" :y2 "800"}]
                [:path {:d "M1574.5,787.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,781.9,1581.4,787.5,1574.5,787.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 780.3062)"} "99535"]
                [:text {:transform "matrix(1 0 0 1 637.1372 780.3062)"} "Travis Vachon"]
                [:text {:transform "matrix(1 0 0 1 896.2832 780.3062)"} "UI for triggering test hooks"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 780.3062)"} "22:13"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "850" :x2 "1600" :y2 "850"}]
                [:path {:d "M1574.5,837.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,831.9,1581.4,837.5,1574.5,837.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 830.3062)"} "99528"]
                [:text {:transform "matrix(1 0 0 1 637.1372 830.3062)"} "Rob Zuber"]
                [:text {:transform "matrix(1 0 0 1 896.2832 830.3062)"} "Adds changelog entry."]
                [:text {:transform "matrix(1 0 0 1 1337.8501 830.3062)"} "23:19"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "900" :x2 "1600" :y2 "900"}]
                [:path {:d "M1574.5,887.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,881.9,1581.4,887.5,1574.5,887.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 880.3062)"} "99526"]
                [:text {:transform "matrix(1 0 0 1 637.1372 880.3062)"} "Jim Rose"]
                [:text {:transform "matrix(1 0 0 1 896.2832 880.3062)"} "fix the test"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 880.3062)"} "21:18"]]
               [:g.draw-tr
                [:line {:x1 "350" :y1 "950" :x2 "1600" :y2 "950"}]
                [:path {:d "M1574.5,937.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,931.9,1581.4,937.5,1574.5,937.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 930.3062)"} "99520"]
                [:text {:transform "matrix(1 0 0 1 637.1372 930.3062)"} "Scout"]
                [:text {:transform "matrix(1 0 0 1 896.2832 930.3062)"} "Allow SDS deployment syntax in circle.yml"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 930.3062)"} "21:39"]]
               [:g.draw-tr
                [:path {:d "M1574.5,987.5h-50c-6.9,0-12.5-5.6-12.5-12.5l0,0c0-6.9,5.6-12.5,12.5-12.5h50c6.9,0,12.5,5.6,12.5,12.5 l0,0C1587,981.9,1581.4,987.5,1574.5,987.5z"}]
                [:text {:transform "matrix(1 0 0 1 400 980.3062)"} "99519"]
                [:text {:transform "matrix(1 0 0 1 637.1372 980.3062)"} "Emily Schuman"]
                [:text {:transform "matrix(1 0 0 1 896.2832 980.3062)"} "delete nested subvolumes"]
                [:text {:transform "matrix(1 0 0 1 1337.8501 980.3062)"} "21:54"]]])))))

(defn drawing-dashboard [app owner]
  (reify
    om/IRender
    (render [_]
      (let [ab-tests (:ab-tests app)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html
          [:svg.drawing.draw-dash {:xmlns "http://www.w3.org/2000/svg"
                                   :x "0px"
                                   :y "0px"
                                   :viewBox "0 0 1600 1000"
                                   :enableBackground "new 0 0 1600 1000"}
           (om/build draw-nav app)
           (om/build draw-menu app)
           (om/build draw-main app)])))))