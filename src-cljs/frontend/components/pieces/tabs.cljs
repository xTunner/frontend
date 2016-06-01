(ns frontend.components.pieces.tabs
  (:require [devcards.core :as dc :refer-macros [defcard defcard-om defcard-doc om-root]]
            [frontend.config :as config]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(defn tab-row
  "A row of tabs, suitable for the top of a card.

  :tabs         - A sequence of tabs, in display order. Each tab is a pair: the first
                  element is the tab name (a unique identifier for the tab, not displayed
                  to the user); the second is the contents of that tab label (not the
                  contents of the tab page--rendering the tab page contents is outside
                  this component's purview. The tab label contents may be a React
                  component or element, or a list of them.
  :selected-tab - The name of the selected tab.
  :on-tab-click - A handler called when a tab is clicked. The handler will receive the
                  name of the clicked tab."
  [{:keys [tabs selected-tab on-tab-click] :as data} owner]
  (reify
    om/IDisplayName (display-name [_] "Tab Row")

    om/IRender
    (render [_]
      (html
       [:ul.nav.nav-tabs
        (for [[tab-name tab-contents] tabs]
          [:li (if (= selected-tab tab-name)
                 {:class "active"}
                 {:on-click #(on-tab-click tab-name)})
           [:a tab-contents]])]))))
