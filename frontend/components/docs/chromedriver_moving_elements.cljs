(ns frontend.components.docs.chromedriver-moving-elements)

(def article
  {:title "ChromeDriver raises an 'Element is not clickable' exception"
   :last-updated "Aug 7, 2013"
   :url :chromedriver-moving-elements
   :content [:div
             [:p
              "This can be caused by the small delay between ChromeDriver determining thelocation of an element to click and actually clicking on the element. If theelement is moving (for instance because another element has loaded and causedthe page to reflow) it is no longer at the coordinates that ChomeDrivercaptured and it tries to click in the wrong place, causing this error."]
             [:p
              "This behaviour is due to the ChromeDriver implementation (there is an"
              [:a
               {:href
                "https://code.google.com/p/chromedriver/issues/detail?id=22"}
               "issue"]
              "tracking a fix in ChromeDriver itself)."]
             [:p
              "You can use"
              [:a
               {:href
                "http://docs.seleniumhq.org/docs/04_webdriver_advanced.jsp#explicit-and-implicit-waits-reference"}
               "explicit waits"]
              "along with a custom expected condition to wait until an element has stopped moving before clicking."]]})
