(ns frontend.components.about
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.components.common :as common]
            [frontend.components.contact-form :as contact-form]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [frontend.utils :refer [defrender html]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn scaled-image-path [name]
  (let [retina (> (.-devicePixelRatio js/window) 1)
        path (gstring/format "/img/outer/about/%s%s.png" name (if retina "@2x" ""))]
    (println retina)
    (println (stefon/asset-path path))
    (stefon/asset-path path)))

(defn team-data []
  [{:name "Paul Biggar"
    :role "Founder"
    :github "pbiggar"
    :twitter "paulbiggar"
    :email "paul@circleci.com"
    :img-path (scaled-image-path "paul")
    :visible true
    :bio "Before founding CircleCI, Paul worked on the Firefox Javascript engine and wrote phc, an open source PHP compiler. He did his PhD on compilers and static analysis in Dublin and is a YCombinator alumni."}
   {:name "Jenneviere Villegas"
    :img-path (scaled-image-path "jenneviere")
    :visible true
    :role "Marketing"
    :github "jenneviere"
    :twitter "jenneviere"
    :email "jenneviere@circleci.com"
    :bio "Jenneviere moved to the Bay Area in 2010 and was CircleCI’s first official employee. When she’s not working, she can be found dancing, singing and acting in movies, television and on stage, or drinking a hoppy IPA at That Thing In The Desert."}
   {:name "Mahmood Ali"
    :img-path (scaled-image-path "mahmood")
    :visible true
    :role "Backend Developer"
    :github "notnoopci"
    :twitter "notnoop"
    :email "mahmood@circleci.com"
    :bio "During his time at MIT, Mahmood spoke at a few Java research conferences includingDevoxx, and has pushed code to Java 8—and Java 7—compilers. He's been a speaker at OOPSLA/Splash, and is active in the open-source community. He and his family have moved 6 times in the last 6 years."}
   {:name "Gordon Syme"
    :img-path (scaled-image-path "gordon")
    :visible true
    :role "Backend Developer"
    :github "gordonsyme"
    :twitter "gordon_syme"
    :email "gordon@circleci.com"
    :bio "Gordon joins the team after spending significant time at Amazon building tools to monitor the entire network (he built their DWDM monitoring from the ground up). When he's not hacking away on code, he spends time mountain biking and racing dinghies off the coast of Ireland."}
   {:name "Tim Dixon"
    :img-path (scaled-image-path "tim")
    :visible true
    :role "Developer"
    :github "startling"
    :email "tim@circleci.com"
    :bio "Tim grew up in the midwest and moved to SF to work for Circle. He likes functional programming, board games, and learning on-the-fly."}
   {:name "Ian Duncan"
    :img-path (scaled-image-path "ian")
    :visible true
    :role "Developer"
    :github "iand675"
    :twitter "iand675"
    :email "ian@circleci.com"
    :bio "Ian lives in South Carolina, where he codes things for CircleCI. He’s a functional programming fanatic, and loves learning how things work. When he’s not working, he’s busy cooking delicious food."}
   {:name "Kevin Bell"
    :img-path (scaled-image-path "kevin")
    :visible true
    :role "Developer Evangelist"
    :github "bellkev"
    :twitter "iamkevinbell"
    :email "kevin@circleci.com"
    :bio "Kevin studied physics at the University of Washington, but has since discovered that software better satisfies his need for instant gratification. He dabbles in jazz piano in his free time, but fortunately for CircleCI he is much better with a computer than he is with a piano."}
   {:name "Jim Rose"
    :img-path (scaled-image-path "jim")
    :visible true
    :role "CEO"
    :email "jim@circleci.com"
    :bio "Jim co-founded Distiller.io, a continuous integration and deployment service for mobile apps, that CircleCI acquired in August 2013. Prior to Distiller, Jim founded several companies in the ecommmerce, search, and social spaces."}
   {:name "Rob Zuber"
    :img-path (scaled-image-path "rob")
    :visible true
    :role "VP Engineering"
    :github "z00b"
    :email "rob@circleci.com"
    :bio "Rob joined CircleCI through the acquisition of Distiller where he was Co-Founder and CTO. He's been involved in startups since '98, and when he's not toiling away on code he can be found snowboarding, playing guitar, and hanging out with his family."}
   {:name "Jonathan Irving"
    :img-path (scaled-image-path "jonathan")
    :visible true
    :role "Developer"
    :github "j0ni"
    :twitter "j0ni"
    :email "jonathan@circleci.com"
    :bio "Jonathan studied Music and the History of Ideas, and dabbled in Parole casework before beginning his career in software. For fun he fiddles with Emacs, Clojure, Ableton Live and several guitars, jamming in his basement with his homegrown teenage rhythm section."}
   {:name "Robin Horca"
    :img-path (scaled-image-path "robin")
    :visible true
    :role "Operations"
    :email "robin@circleci.com"
    :bio "Robin was a carrot in a Burning Man rock opera and used to build giant gingerbread houses for the Fairmont. She lived in Costa Rica for 4 years and will tell you all about it; again, and again, and again..."}
   {:name "Cayenne Geis"
    :img-path (scaled-image-path "cayenne")
    :visible true
    :role "Developer"
    :github "cayennes"
    :email "cayenne@circleci.com"
    :bio "Cayenne hails from the Boston area, and enjoys Clojure so much that she uses it for hobby projects even after writing in it all day for work. She's fond of solarized rainbow parentheses, and when she's not coding, she likes playing board games (Dominion!) and handbells."}
   {:name "Tim Reinke"
    :img-path (scaled-image-path "timr")
    :visible true
    :role "Developer"
    :github "musicalchair"
    :twitter "timreinke"
    :email "tim.reinke@circleci.com"
    :bio "Tim studied Electrical Engineering and enjoys programming languages, skiing, systems of all sorts, and helping people get on the same page."}
   {:name "Mike Stearns"
    :img-path (scaled-image-path "mike")
    :visible true
    :role "Marketing"
    :email "mike@circleci.com"
    :bio "After winning both the math and spelling bees in the 5th grade, Mike went on to work in the world of digital marketing and ecommerce. Aside from playing with his wife and daughter, Mike is the President of the San Francisco Homebrewers Guild in his spare time."}
   {:name "Dan Beere"
    :img-path (scaled-image-path "dan")
    :visible true
    :role "Designer"
    :github "danbeere"
    :email "daniel.beere@circleci.com"
    :bio "Dan organized Ireland’s first and second Design Jam (in Limerick and Dublin, respectively) and once climbed Kilimanjaro for charity. A recent transplant from Ireland to San Francisco, he spends his free time exploring his new city."}
   {:name "Laura Franzese"
    :img-path (scaled-image-path "laura")
    :email "laura@circleci.com"
    :role "Public Relations"
    :bio "Laura helps the public to better understand CircleCI. Previously, she worked on strategic communications campaigns for HP, Sun Microsystems and AppDynamics, to name a few. Laura is passionate about stand-up comedy, bunny gif's and the muppets."}
   {:name "Marc O'Morain"
    :img-path (scaled-image-path "marc")
    :email "marc@circleci.com"
    :role "Developer"
    :github "marcomorain"
    :bio "Marc is a reformed game developer, based in Dublin. He still enjoys tinkering with virtual machines and C in his spare time"}
   {:name "Alexey Klochay"
    :img-path (scaled-image-path "alexey")
    :email "alexey@circleci.com"
    :role "Support Engineer"
    :github "appplemac"
    :bio "Alexey joins Circle from the sunny coasts of Spain. In between mountain biking, partying at art galleries and doing martial arts, he helps to ensure that Circle’s customers get the smoothest experience possible."}
   {:name "Hirokuni Kim"
    :img-path (scaled-image-path "")
    :role ""
    :github ""
    :email ""
    :bio "Hirokuni has a bit strange background. He studied political science in college to become a lawyer, but suddenly realized that what he truly loves is computer things, not law. He's been doing ops, devs, and devops in his previous jobs. He loves cooking and playing Shogi (Japanese chess) when he is not busy."}
   {:name "Conor McDermottroe"
    :img-path (scaled-image-path "")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Peter Jaros"
    :img-path (scaled-image-path "")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Katie Ballinger"
    :img-path (scaled-image-path "")
    :role ""
    :github ""
    :email ""
    :bio "Katie is a both a geek and tinkerer at heart and is always making things. From electronics and rapid prototyping to sewing plush toys, she's always trying new things. She frequents meetups with fellow geeks and often spends her nights coding or playing video games."}
   {:name "Kunal Jain"
    :img-path (scaled-image-path "kunal")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Frank Wang"
    :img-path (scaled-image-path "")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Jen Hugo"
    :img-path (scaled-image-path "jen")
    :role ""
    :github ""
    :email ""
    :bio "Jen joins CircleCI after having recruited for a variety of tech companies (Apple, Shutterfly, and Brightedge, to name just a few). She's a proud alum of the Ohio State University--go Bucks!--and loves super hot coffee and spending time in wine country."}
   {:name "Travis Vachon"
    :img-path (scaled-image-path "travis")
    :role ""
    :github ""
    :email ""
    :bio "Travis enjoys climbing, dancing, and desert based art festivals. At Circle he makes sure new features flow steadily from the shifting sands of product design to the thriving robot metropolis at the heartof Circle's build system."}
   {:name "Chris Black"
    :img-path (scaled-image-path "chris")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Jia Le"
    :img-path (scaled-image-path "jia")
    :role ""
    :github ""
    :email ""
    :bio "Jia is the one of the first Account Managers to join the CircleCi team. When she's not busy figuring out customer pain points, you can find her hiking, running half marathons or finding the best new vegetarian restaurant in the Bay."}
   {:name "Chloé Arthrus"
    :img-path (scaled-image-path "")
    :role ""
    :github ""
    :email ""
    :bio "Chloé is a Northwestern University alumna with an extensive background in Advertising and Marketing. She likes Tetris, cookies, oxford commas, and helping your business get the most out of CircleCI. When she's not at her desk, you can find Chloé playing with her cat, Kitty."}
   {:name "Lev Lazinskiy"
    :img-path (scaled-image-path "lev")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Jonathan Morris"
    :img-path (scaled-image-path "jonathan-morris")
    :role ""
    :github ""
    :email ""
    :bio "When not working on all things sales, Jonathan can be found playing beach volleyball or soccer, reading sci-fi and autobiographies, or hacking out Black Bird on the guitar."}
   {:name "Thomas Wong"
    :img-path (scaled-image-path "thomas")
    :role ""
    :github ""
    :email ""
    :bio "Thomas is an alumnus of The University of Southern California. He's excited to help CircleCI grow with the Account Management team. In his free time, you can find Thomas playing playing sports, exploring everything San Francisco, and, naturally, rooting on Trojan Athletics."}
   {:name "Parth Patel"
    :img-path (scaled-image-path "")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Sean Heuer"
    :img-path (scaled-image-path "")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Rishi Kumar"
    :img-path (scaled-image-path "rishi")
    :role ""
    :github ""
    :email ""
    :bio "Rishi joins CircleCI to work on Product + Growth.  He's worked at a couple of startups, most recently Infer. He spent some time in VC while getting his MBA and previously worked in Management Consulting. He loves UNC basketball, golf pants, travel, and trivia nights."}
   {:name "Le Wang"
    :img-path (scaled-image-path "")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Mike Rosabal"
    :img-path (scaled-image-path "mike-rosabal")
    :role ""
    :github ""
    :email ""
    :bio ""}
   {:name "Justin Cowperthwaite"
    :img-path (scaled-image-path "justin")
    :role ""
    :github ""
    :email ""
    :bio "Justin, a USC graduate, joins the CircleCI team as a web developer. As a product minded software engineer, Justin is excited to grow the CircleCI web platform. As a UX enthusiast, he's excited to continuously improve the usability of the site. In his spare time Justin likes playing tennis, bike riding, and most importantly, traveling the world."}
])



(defn contact-image-src [shortname]
    (utils/cdn-path (gstring/format "/img/outer/contact/contact-%s.svg" shortname)))


(def contact-form
  (contact-form/contact-form-builder
    {:class "contact-us"
     :action "/about/contact"}
    (fn [control notice form-state]
      (list
        [:h2.form-header "We'd love to hear from you!"]
        [:div.row
         [:div.form-group.col-xs-6
          (control :input.dumb.form-control
                   {:placeholder "Name"
                    :aria-label "Name"
                    :disabled (= :loading form-state)
                    :required true
                    :type "text"
                    :name "name"})]
         [:div.form-group.col-xs-6
          (control :input.dumb.form-control
                   {:placeholder "Email"
                    :aria-label "Email"
                    :disabled (= :loading form-state)
                    :type "email"
                    :name "email"
                    :required true})]]
        [:div.form-group
         (control :textarea.dumb.form-control.message
                  {:placeholder "Tell us what you're thinking..."
                   :aria-label "Tell us what you're thinking..."
                   :disabled (= :loading form-state)
                   :required true
                   :name "message"})]
        (om/build contact-form/transitionable-height
                  {:class "notice"
                   :children (html
                               (when notice
                                 [:div {:class (:type notice)}
                                  (:message notice)]))})
        (om/build contact-form/morphing-button {:text "Send" :form-state form-state})
        [:div.success-message
         {:class (when (= :success form-state) "success")}
         "Thanks for getting in touch!"]))))



(defn contact [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div#contact
        [:div.jumbotron
         common/language-background-jumbotron
         [:section.container
          [:div.row
           [:article.hero-title.center-block
            [:div.text-center
             [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]]
            [:h1.text-center "Contact Us"]]]]]
        [:div.outer-section
         [:section.container
          [:div.row
           [:div.contact.col-xs-3
            [:img.logo {:src (contact-image-src "mail")}]
            [:p.header "Email us at"]
            [:p.value [:a {:href "mailto:sayhi@circleci.com"} "sayhi@circleci.com"]]]
           [:div.contact.col-xs-3
            [:img.logo {:src (contact-image-src "twitter")}]
            [:p.header "Tweet us at"]
            [:p.value [:a {:href "https://twitter.com/circleci"} "@circleci"]]]
           [:div.contact.col-xs-3
            [:img.logo {:src (contact-image-src "map")}]
            [:p.header "Visit us at"]
            [:p.value [:a {:href "https://goo.gl/maps/uhkLn"} "555 Market Street"]]]
           [:div.contact.col-xs-3
            [:img.logo {:src (contact-image-src "call")}]
            [:p.header "Call us at"]
            [:p.value [:a {:href "tel:+18005857075"} "1-800-585-7075"]]]]]]
;<iframe src="https://www.google.com/maps/embed?pb=!1m14!1m8!1m3!1d1576.5031953493105!2d-122.39993!3d37.78989!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x80858062f5233729%3A0x8e66673bca8fcf51!2s555+Market+St%2C+San+Francisco%2C+CA+94105!5e0!3m2!1sen!2sus!4v1427412433448" width="600" height="450" frameborder="0" style="border:0"></iframe>
        [:div.outer-section
         [:iframe.map {:src "https://www.google.com/maps/embed?pb=!1m14!1m8!1m3!1d1576.5031953493105!2d-122.39993!3d37.78989!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x80858062f5233729%3A0x8e66673bca8fcf51!2s555+Market+St%2C+San+Francisco%2C+CA+94105!5e0!3m2!1sen!2sus!4v1427412433448"}]]
        [:div.outer-section
         [:section.container
          [:div.row
           [:div.col-xs-6.col-xs-offset-3
            (om/build contact-form nil)]]]]]))))

(defn customer-image-src [shortname]
  (utils/cdn-path (gstring/format "/img/outer/about/logo-%s.svg" shortname)))

(defn about [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div#about.product-page
        [:div.jumbotron
         common/language-background-jumbotron
         [:section.container
          [:div.row
           [:article.hero-title.center-block
            [:div.text-center
             [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]]
            [:h1.text-center "About Us"]
            [:h3.text-center "CircleCI was founded in 2011 with the mission of giving every developer state-of-the-art automated testing and continuous integration tools."]]]]
         [:div.row.text-center
          (common/sign-up-cta owner "about")]]
        [:div.outer-section
         [:section.container
          [:div.row
           [:div.fact.col-xs-4
            [:h3.header "Born"]
            [:p.value "2011"]
            [:p.caption "CircleCI was founded in 2011"]]
           [:div.fact.col-xs-4
            [:h3.header "Team"]
            [:p.value "40+"]
            [:p.caption
             [:a {:href "/jobs"} "Join"]
             " our growing team in downtown SF"]]
           [:div.fact.col-xs-4
            [:h3.header "Raised"]
            [:p.value "$6M"]
            [:p.caption
             "In 2014 we raised a $6m Series A Round from "
             [:a {:href "http://dfj.com/" :target "_blank"} "DFJ"]]]]
          [:div.row
           [:div.story.col-xs-12
            [:h2 "Our story"]
            [:p
             "CircleCI provides development teams the confidence to build, test, "
             "and deploy—quickly and consistently—across numerous platforms. Built to address "
             "the demanding needs of today's application development environments, CircleCI "
             "supports every component of a modern application, including mobile apps (iOS and Android), "
             "conventional web applications (built with platforms like Rails and Django), "
             "browser-based apps (built with frameworks like AngularJS and Ember), "
             "and containerized apps (built with tools like Docker)."]
            [:p
             "CircleCI makes continuous integration and continuous deployment simple and easy for "
             "thousands of companies like Shopify, Cisco, Sony and Trunk Club, so they can ship "
             "better code, faster."]
            [:p
             "CircleCI is venture backed by Draper Fisher Jurvetson, Baseline Ventures, Harrison "
             "Metal Capital, Data Collective, 500 Startups, SV Angel, and a collection of respected "
             "angels including James Lindenbaum and Adam Wiggins (Heroku), Jason Seats (Slicehost), "
             "Eric Ries (The Lean Startup), and Hiten Shah (Kissmetrics)."]]]
          [:div.row
           [:div.logo-container.col-sm-12
            (for [customer ["shopify" "cisco" "sony" "trunkclub"]]
              [:img.logo {:src (customer-image-src customer)}])]]]]
        [:div.bottom-cta.outer-section.outer-section-condensed
         common/language-background
         [:h2 "Start shipping faster, build for free using CircleCI today."]
         [:p.subheader
          "You have a product to focus on, let CircleCI handle your continuous integration and deployment."]
         (common/sign-up-cta owner "about")]]))))

(defrender team [app owner]
  (html
   [:div#team
    [:div.jumbotron
     common/language-background-jumbotron
     [:section.container
      [:div.row
       [:article.hero-title.center-block
        [:div.text-center
         [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]]
        [:h1.text-center "Meet the Team"]
        [:h3.text-center "We love to build software that makes our customers successful."]]]]]
    [:div.outer-section.people-section
     [:section.container
      [:h2.people-header "Meet the team!"]
      [:div.people
       (for [person (team-data)]
         [:div.person
          [:div.circle
           [:div.bubble
            (:bio person)]
           [:div.pic
            (if-let [img-path (:img-path person)]
              [:img.headshot {:src img-path}]
              [:img.logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}])]]
          [:p.name (:name person)]
          [:p.title (:role person)]])]]]
    [:div.bottom-cta.outer-section.outer-section-condensed
     common/language-background
     [:h2 "We're Hiring"]
     [:p.subheader "We're looking for amazing people to join us on this journey, want to join the team?"]
     [:a.btn.btn-cta
      {:href "/jobs"}
      "Join Us"]]]))
