(ns frontend.components.jobs
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.components.common :as common]
            [frontend.components.features :as features]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(defn jobs-icon [name]
  [:img.header-icon {:src (utils/cdn-path (str "/img/outer/about/jobs-" name ".svg"))}])

(defn jobs [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.jobs
        [:div.jumbotron
         common/language-background-jumbotron
         [:section.container
          [:div.row.text-center
           [:article.hero-title.center-block
            [:div.text-center
             [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]]
            [:h1.text-center
             "Build amazing products"
             [:br]
             "with a great team"]]
           [:a.btn.btn-cta
            {:href "https://jobs.lever.co/circleci/"}
            "View openings"]]]]
        [:div.container-fluid
         [:div.row.jobs-intro-wrapper
          [:div.col-sm-6.jobs-intro
           [:h2 "Who We Are"]
           [:p
            "CircleCI is a rapidly growing company with a great mission, a profoundly talented team, and a focus on productivity for both our customers and ourselves. We've an empowering, transparent organization, a great culture, and a delightful product that's loved by thousands of developers. With significant revenue and funding, we're just getting started with taking developer tools to the next level."]]
          [:div.col-sm-6.example-shot
           (features/ui-example "dash")]]]
        [:div.job-reasons.outer-section
         [:section.container
          [:h2.text-center "Why you'll love working at CircleCI"]
          [:div.row
           [:div.col-sm-5.col-sm-offset-1
            (jobs-icon "effect")
            [:h3.text-center "At a small growing company, your work has a disproportionate effect."]
            [:p
             "As well as improving our customers' lives, you'll also set a precedent for everyone who works for us in the future. You get to affect both the product and the company culture. Your opinions matter and are listened to, and that's why we hire you."]]
           [:div.col-sm-5
            (jobs-icon "customers")
            [:h3.text-center "Our culture is customer-driven."]
            [:p
             "Founded by developers, we grow and change based on the feedback we get from our customers. Everyone in the company is dedicated to developer productivity."]]]
          [:div.row
           [:div.col-sm-5.col-sm-offset-1
            (jobs-icon "open")
            [:h3.text-center "We have an empowering organization."]
            [:p
             "Everyone gets access to all company information and can be involved in decision-making. By keeping communication open and transparent, we believe everybody is able to make good decisions about the best thing to work on."]]
           [:div.col-sm-5
            (jobs-icon "productivity")
            [:h3.text-center
             "We have a very strong focus on productivity."]
            [:p
             "This means you'll have a lot of days where you finish the day with the happy glow of having achieved something. We shape our work lives around enabling that for all of our employees."]]]
          [:div.row
           [:div.col-sm-5.col-sm-offset-1
            (jobs-icon "care")
            [:h3.text-center "We take good care of you."]
            [:p
             "We are investing in you long term, and not just from nine to five. We offer competitive salaries and equity, as well as comprehensive health, dental, and vision benefits for you and your family."]]
           [:div.col-sm-5
            (jobs-icon "customers")
            [:h3.text-center "We spend a lot of time talking to customers."]
            [:p "We make it really easy for customers to reach out to us, and we reach out to them to ask for their feedback too. We pride ourselves in being customer-driven."]
            ]]
          [:div.row
           [:div.col-sm-5.col-sm-offset-1
            (jobs-icon "tech")
            [:h3 "We use cool technology."]
            [:p
             "We utilize the most up-to-date and cutting edge technologies to keep our team interconnected and efficient, even across multiple time zones (and for the engineers, our backend is written in Clojure, and our front end is open-sourced)."]]
           [:div.col-sm-5
            (jobs-icon "learn")
            [:h3.text-center "There's lots to learn."]
            [:p
             "We try to hire really, really high caliber people, which means you'll be able to learn a lot, and we hope to learn a lot from you too. We look for the very experienced, or the very talented. If you work at CircleCI, you know that you won't be on the team with slouches or slackers, or folks who will hold you back. (Curiously, we disproportionately hire folks who find it hard to believe they're as amazing as they are, and suffer from significant imposter syndrome. Go figure. If this matches you, we encourage you to apply anyway)."]]]]]
        [:div.jobs-where
         [:div.container-fluid
          [:div.row
           [:div.col-sm-6.map-wrapper
            [:iframe.map {:src "https://www.google.com/maps/embed?pb=!1m14!1m8!1m3!1d1576.5031953493105!2d-122.39993!3d37.78989!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x80858062f5233729%3A0x8e66673bca8fcf51!2s555+Market+St%2C+San+Francisco%2C+CA+94105!5e0!3m2!1sen!2sus!4v1427412433448"}]]
           [:div.col-sm-6.where-text
            [:h2 "Where We Work"]
            [:h3 "Local"]
            [:p
             "Our team is dedicated to creating the most productive environment possible. That means we ensure everyone is able to enjoy their own personal level of comfort and privacy. We work in San Francisco's beautiful Financial District, where you'll have a "
             [:a
              {:href "http://blog.circleci.com/silence-is-for-the-weak"}
              "private office"]
             ", catered lunch and lovely colleagues."]
            [:h3 "Remote"]
            [:p
             "We have an asynchronous culture that is well suited to remote-working. We use Slack, Hangouts and email heavily, and roughly half of our engineers work remotely. We'll bring you on-site every 10 weeks or so to work closely with your colleagues, and can do this to fit your remote life."]]]]
         [:div.outer-section.outer-section-condensed.job-cta
          common/language-background
          [:section.container
           [:div.col-xs-12.text-center
            [:h3 "We're looking for amazing people to join us on this journey. Want to join the team?"]
            [:a.btn.btn-cta {:href "https://jobs.lever.co/circleci/"} "View openings"]]]]]]))))
