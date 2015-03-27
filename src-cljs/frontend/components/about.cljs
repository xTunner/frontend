(ns frontend.components.about
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.components.common :as common]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [om.core :as om :include-macros true]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [frontend.utils :refer [defrender html]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn team []
  [{:name "Paul Biggar"
    :role "Founder"
    :github "pbiggar"
    :twitter "paulbiggar"
    :email "paul@circleci.com"
    :img-path (stefon/asset-path "/img/outer/about/paul.png")
    :visible true
    :bio ["Paul Biggar is cofounder and CEO of CircleCI. In past lives, he wrote phc, an open source PHP compiler, while doing his PhD on compilers and static analysis in Dublin. He has been accepted to YCombinator, given talks at Facebook and Google, been interviewed by the Wall Street Journal, and once (mostly) landed a backflip on skis!",
          "After moving to the Bay Area in 2011, Paul worked on the Firefox Javascript engine, then he co-founded CircleCI. Because it pains Paul to see so much time wasted due to inadequate developer tools, he approaches continuous integration and delivery from the perspective of developer productivity. He loves to focus on building the CircleCI product, but is currently kept so busy taking care of the business side of things that his coding fingers are getting restless."]}
    {:name "Jenneviere Villegas"
    :img-path (stefon/asset-path "/img/outer/about/jenneviere.png")
    :visible true
    :role "Operations"
    :github "jenneviere"
    :twitter "jenneviere"
    :email "jenneviere@circleci.com"
    :bio ["Jenneviere moved to the Bay Area in 2010 after spending a few months there, singing and dancing, as the lead in a rock opera. She then worked as an extra on the movie Twixt, and when she realized that Mr. Coppola wasn't going to cast her as the lead in his next film, she began drowning her sorrows in hoppy IPAs and the soothing click of knitting needles. She took the black and works the Gate at That Thing In The Desert, and has only written a single line of code, ever.",
          "Drawing from her many years' experience in her previous position as the Customer Amazement Specialist, Operations Manager, Returns Siren, and Retail Store Maven for a certain large utility kilt manufacturer, Jenneviere brings her skills as a professional pottymouth, dabbler in inappropriate and snarky humor, and cat wrangling to the team at Circle, and spends most of her time trying to keep everyone well-groomed and hairball free."]}
   {:name "David Lowe"
    :visible true
    :role "Backend Developer"
    :github "dlowe"
    :twitter "j_david_lowe"
    :email "dlowe@circleci.com"
    :img-path (stefon/asset-path "/img/outer/about/david.png")
    :bio ["David is responsible for all of the bugs. He wrote his first buggy code in BASIC with a pencil and paper, and he's been getting better at it ever since. He keeps a 1/4 acre garden and eats almost entirely home-grown veggies during the growing season. Helping to balance out the numerous times that he's lost at chess, David has won the IOCCC on 6 separate occasions. In a past life, he wrote MTA software which at one time was sending about 1% of the internet's email.",
          "Besides being invited to speak at the Frozen Perl Conference, OIT's CSET Department, and SOU's CS Department, David co-founded the SF perl user group, the southern Oregon geek group, and the Rogue hack lab hackerspace. He drank the automated testing kool-aid years ago, and after introducing and championing (and constantly fiddling with) continuous integration tools at his last N jobs, he came to Circle, and is particularly keen on building and scaling things which would be impossible to justify for any single small company where CI isn't what they do. He splits his time between introducing new bugs, telling kids to get off his lawn, and baking pies. His current running total is roughly 1500 delicious pastry concoctions."]}
   {:name "Mahmood Ali"
    :img-path (stefon/asset-path "/img/outer/about/mahmood.png")
    :visible true
    :role "Backend Developer"
    :github "notnoopci"
    :twitter "notnoop"
    :email "mahmood@circleci.com"
    :bio ["Mahmood loves car camping, which might explain why he also likes moving as much as he does. He and his family have moved 6 times in the last 6 years, though his baby boy has been completely lazy and hasn't offered to carry a single box yet. In between relocation to new abodes, Mahmood spends the nicer Boston weather exploring bike trails and learning computer languages. He's determined that his son will know Java before he can talk.",
          "During his time at MIT, Mahmood spoke at a few Java research conferences including Devoxx, and has pushed code to Java 8--and Java 7--compilers. He's been a speaker at OOPSLA/Splash, and is active in the open-source community. He enjoys being reminded of his in-production projects, like java-apns, every time he gets support tickets. He acknowledges his own fallibility, and aims to help the Circle team continue to bring reliable test automation to all developers. His Achilles Heel is his pair of permanently underachieving glasses."]}
   {:name "Gordon Syme"
    :img-path (stefon/asset-path "/img/outer/about/gordon.png")
    :visible true
    :role "Backend Developer"
    :github "gordonsyme"
    :twitter "gordon_syme"
    :email "gordon@circleci.com"
    :bio ["Gordon has been racing dinghies right through the winter off the coast of Ireland for the past 16-odd years, and as his oft-showcased cabinet of prize coffee mugs will tell you, he's damn good at it. When he's not busy bludgeoning a piano with his ham-fisted fingers, he spends his free time rolling down mountains on a bicycle… sometimes even the right way up. In his down time, he's a board game shark, though you won't catch him trying to buy Boardwalk or get out of jail free.",
          "Gordon joins the team after spending significant time at Amazon building tools to monitor the entire network (he built their DWDM monitoring from the ground up). He also built a JVM bytecode recompiler to enable running applications on a clustered VM without needing re-programming effort, and has grand plans for how he's going to make waves at Circle. We're pretty sure they include company Power Grid tournaments."]}
   {:name "Nick Gottlieb"
    :img-path (stefon/asset-path "/img/outer/about/nick.png")
    :visible true
    :role "Marketing Engineer"
    :github "worldsoup"
    :twitter "worldsoup"
    :email "nick@circleci.com"
    :bio ["Nick is an aspiring merman who spends most of his time outside of the Circle office in the ocean; surfing, diving, sailing, and swimming. He lived in Japan for 3 years as a student, consultant, actor, and vagabond and wrote his senior thesis on the cultural importance of baseball in the country. He won a hackathon by building a social haiku app for iPhone (which is still in the App Store). Eventually he came back to the US and got his first ‘real job’ at a digital consultancy where he optimized conversion rates for websites that comprise hundreds of thousands of visitors and generate millions in revenue every month.",
          "In 2012 Nick was lured to San Francisco by the prospect of hitting it big with his own startup, which crashed and burned, but he liked the place so decided to stick around. He loves the open work culture at Circle which allows him to fulfill his constant need to get-shit-done while helping developers do the same. One day he will unplug his computer and sail around the world."]}
   {:name "Emile Snyder"
    :img-path (stefon/asset-path "/img/outer/about/emile.png")
    :visible true
    :role "Backend Developer"
    :github "esnyder"
    :twitter "emilesnyder"
    :email "emile@circleci.com"}
   {:name "Tim Dixon"
    :img-path (stefon/asset-path "/img/outer/about/tim.png")
    :visible true
    :role "Developer"
    :github "startling"
    :email "tim@circleci.com"}
   {:name "Ian Duncan"
    :img-path (stefon/asset-path "/img/outer/about/ian.png")
    :visible true
    :role "Developer"
    :github "iand675"
    :twitter "iand675"
    :email "ian@circleci.com"
    :bio ["Ian joins the CircleCI team remotely from South Carolina. When he’s not busy coding away on CircleCI, he’s probably off hacking on his Haskell side projects or studying distributed systems with a quality microbrew in hand. He started learning Haskell in high school for some research projects, and has been hooked on functional programming ever since. Ian's always had more ideas for things to build than time on his hands, but managed to win runner up in a few entrepreneurship competitions in college for his work.",
          "He’s particularly fond of travel, and has done two cross-country moves in 2 years. He loves getting to know foreign cultures, and has learned to speak some Japanese and French along the way. During the summer months, he spends his time bicycling, hiking, and running, and once the weather turns cold he hones his gaming skills on Dance Dance Revolution and Super Smash Bros, sits back and binge-watches television shows (currently: Castle and Brooklyn Nine-Nine), and chows down on pumpkin pie."]}
   {:name "Kevin Bell"
    :visible true
    :role "Developer Evangelist"
    :github "bellkev"
    :twitter "iamkevinbell"
    :email "kevin@circleci.com"}
   {:name "Cayenne Geis"
    :img-path (stefon/asset-path "/img/outer/about/cayenne.png")
    :visible true
    :role "Developer"
    :github "cayennes"
    :email "cayenne@circleci.com"}
   {:name "Jonathan Irving"
    :img-path (stefon/asset-path "/img/outer/about/jonathan.png")
    :visible true
    :role "Developer"
    :github "j0ni"
    :twitter "j0ni"
    :email "jonathan@circleci.com"}
   {:name "Tim Reinke"
    :img-path (stefon/asset-path "/img/outer/about/timr.png")
    :visible true
    :role "Developer Success"
    :github "musicalchair"
    :twitter "timreinke"
    :email "tim.reinke@circleci.com"}
   {:name "Travis Vachon"
    :visible true
    :role "Developer"
    :github "travis"
    :twitter "tvachon"
    :email "travis@circleci.com"}])

(def placeholder-image [:svg.about-placeholder {:height "150px" :width "150px" :viewBox "0 0 150 150"}
                        [:path.bust {:d "M-1.673,151.75v-13.856c0,0,0.901-8.505,11.143-11.142c0,0,16.495-6.022,29.415-11.646 c6.681-2.907,8.119-4.652,14.859-7.631c0,0,0.703-3.461,0.45-5.521h5.271c0,0,1.204,0.702,0-7.43c0,0-6.425-1.705-6.725-14.655 c0,0-4.825,1.625-5.121-6.224c-0.202-5.321-4.318-9.939,1.607-13.753l-3.012-8.131c0,0-6.023-32.826,11.294-28.008 c-7.305-8.734,41.409-17.465,44.574,10.24c0,0,2.256,14.958,0,25.197c0,0,7.113-0.824,2.357,12.852c0,0-2.609,9.836-6.627,7.628 c0,0,0.656,12.447-5.668,14.559c0,0,0.449,6.625,0.449,7.075l6.025,0.903c0,0-0.906,5.424,0.152,6.022c0,0,7.142,4.894,15.657,7.081 c16.414,4.212,35.837,11.44,35.837,17.766c0,0,1.659,8.435,1.659,18.673H-1.673z",
                                     :fill "#ccc"}]])

(defn placeholder-bio [name]
  [:div.bio-coming-soon
   [:p
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi iaculis mi ante, at mattis purus varius sed. Maecenas sollicitudin volutpat nunc eget volutpat. Fusce sollicitudin adipiscing tincidunt. Duis tincidunt quis eros id egestas. Nunc ac nisi mollis, egestas enim quis, bibendum odio. Vestibulum facilisis lorem ante, ut placerat elit pellentesque non. Maecenas blandit urna non pharetra venenatis. Quisque malesuada, tellus nec porta luctus, mi lorem mattis elit, sit amet semper neque ligula sed orci. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Suspendisse eu lectus lobortis, condimentum orci faucibus, porta leo. Donec pretium sapien sit amet orci rhoncus, in suscipit erat fringilla. Donec viverra nulla dolor. Nam id libero ultricies, viverra nisi et, commodo lorem. Maecenas ac risus lobortis, pellentesque ante in, consectetur nulla."]
   [:div.coming-soon
    {:placeholder (str "Sorry, we're still working on " (first (str/split name #" ")) "'s bio.")}]])

(defn contact-image-src [shortname]
    (utils/cdn-path (gstring/format "/img/outer/contact/contact-%s.svg" shortname)))

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
      (let [clear-notice! #(om/set-state! owner [:notice] nil)
            enterprise? (:enterprise? opts)]
        (html
         [:form.contact-us
          [:h2.form-header "We'd love to hear from you!"]
          [:div.row
           [:div.form-group.col-xs-6
            [:label.sr-only {:for "name"} "Name"]
            [:input.dumb.form-control
             {:value name
              :placeholder "Name"
              :required true
              :class (when loading? "disabled")
              :type "text"
              :name "name"
              :on-change #(do (clear-notice!) (om/set-state! owner [:name] (.. % -target -value)))}]]
           [:div.form-group.col-xs-6
            [:label.sr-only {:for "email"} "Email"]
            [:input.dumb.form-control
             {:value email
              :placeholder "Email"
              :class (when loading? "disabled")
              :type "email"
              :name "email"
              :required true
              :on-change #(do (clear-notice!) (om/set-state! owner [:email] (.. % -target -value)))}]]]
          [:div.form-group
           [:label.sr-only {:for "message"} "Message"]
           [:textarea.dumb.form-control.message
            {:value message
             :placeholder "Tell us what you're thinking..."
             :class (when loading? "disabled")
             :required true
             :name "message"
             :on-change #(do (clear-notice!) (om/set-state! owner [:message] (.. % -target -value)))}]]
          [:div.notice (when notice
                         [:div {:class (:type notice)}
                          (:message notice)])]
          [:button.btn.btn-cta {:class (when loading? "disabled")
                                :on-click #(do (cond
                                                (not (and (seq name) (seq email) (seq message)))
                                                (om/set-state! owner [:notice] {:type "error"
                                                                                :message "All fields are required."})

                                                (not (utils/valid-email? email))
                                                (om/set-state! owner [:notice] {:type "error"
                                                                                :message "Please enter a valid email address."})

                                                :else
                                                (do
                                                  (om/set-state! owner [:loading?] true)
                                                  (go (let [resp (<! (ajax/managed-form-post
                                                                      "/about/contact"
                                                                      :params (merge {:name name
                                                                                      :email email
                                                                                      :message message}
                                                                                     (when enterprise?
                                                                                       {:enterprise enterprise?}))))]
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
            [:p.value [:a {:href "tel:+18005857076"} "1-800-585-7076"]]]]]]
;<iframe src="https://www.google.com/maps/embed?pb=!1m14!1m8!1m3!1d1576.5031953493105!2d-122.39993!3d37.78989!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x80858062f5233729%3A0x8e66673bca8fcf51!2s555+Market+St%2C+San+Francisco%2C+CA+94105!5e0!3m2!1sen!2sus!4v1427412433448" width="600" height="450" frameborder="0" style="border:0"></iframe>
        [:div.outer-section
         [:iframe.map {:src "https://www.google.com/maps/embed?pb=!1m14!1m8!1m3!1d1576.5031953493105!2d-122.39993!3d37.78989!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x80858062f5233729%3A0x8e66673bca8fcf51!2s555+Market+St%2C+San+Francisco%2C+CA+94105!5e0!3m2!1sen!2sus!4v1427412433448"}]]
        [:div.outer-section
         [:section.container
          [:div.row
           [:div.col-xs-6.col-xs-offset-3
            (om/build contact-form app)]]]]]))))

(defn customer-image-src [shortname]
  (utils/cdn-path (gstring/format "/img/outer/about/logo-%s.svg" shortname)))

(defn about [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div#about
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
            [:p.value "25+"]
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
