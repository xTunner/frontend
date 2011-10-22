(ns circle.web.test-web
  (:require circle.init)
  (:require [clj-http.client :as http])
  (:require [clj-http.core :as core])
  (:require [uuid])
  (:use [circle.db :only (with-conn)])
  (:require [circle.model.beta-notify :as beta])
  (:use midje.sweet))

(def site "http://localhost:8080")

(fact "/ returns 200"
  (let [response (http/get site)
        body (response :body)]
    response => (contains {:status 200})
    body => #"Sign up"
    body => #"Continuous Integration made easy"
    body => #"form"
    body =not=> #"Thanks"
    ))

(fact "posting works"
  (let [session (uuid/uuid)
        email (str session "@test.com")
;        db-entries-before (with-conn beta/find-one :where {:email email})
        post-request {:form-params {:email email :contact true}}
        post-response (http/post site post-request)
        get-request {:cookies (post-response :cookies)
                     :url (get-in post-response [:headers "location"])}
        get-response (http/get site get-request)
        body (get-response :body)
;        db-entries-after (with-conn beta/find-one :where {:email email})
        ]

    post-response => (contains {:status 302})
    (:headers post-response) => (contains {"location" "/"})
    get-response => (contains {:status 200})
    body => #"Thanks"
    body =not=> #"form"
;    (println db-entries-after)
;    (println db-entries-after)
;    (count db-entries-before) => 0
;    (count db-entries-after) => 1
    ))

