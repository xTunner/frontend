(ns circle.web.test-web
  (:require circle.init)
  (:require [clj-http.client :as http])
  (:require [clj-http.core :as core])
  ;; (:require [uuid])
  (:require [somnium.congomongo :as mongo])
  (:use midje.sweet))

(def site "http://localhost:8080")

(circle.init/init)

(fact "/ returns 200"
  (let [response (http/get site)
        body (response :body)]
    response => (contains {:status 200})
    body => #"Sign up"
    body => #"Continuous Integration made easy"
    body => #"form"
    body =not=> #"Thanks"))

(fact "posting works"
  (let [session (gensym "foobar") ;;(uuid/uuid)
        email (str session "@test.com")
        db-entries-before (mongo/fetch-count :signups :where {:email email})
        post-request {:form-params {:email email :contact true}}
        post-response (http/post site post-request)
        get-request {:cookies (post-response :cookies)
                     :url (get-in post-response [:headers "location"])}
        get-response (http/get site get-request)
        body (get-response :body)
        db-entries-after (mongo/fetch-count :signups :where {:email email})]

    post-response => (contains {:status 302})
    (:headers post-response) => (contains {"location" "/"})
    get-response => (contains {:status 200})
    body => #"Thanks"
    body =not=> #"form"
    db-entries-before => 0
    db-entries-after => 1))

