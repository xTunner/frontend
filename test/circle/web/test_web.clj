(ns circle.web.test-web
  (:require circle.init)
  (:require [clj-http.client :as http])
  (:require [clj-http.core :as core])
  (:use midje.sweet))

(circle.init/init)

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
      (let [post-request {:form-params {:email "mytest@test.com" :contact true}}
            post-response (http/post site post-request)
            get-request {:cookies (post-response :cookies)
                         :url (get-in post-response [:headers "location"])}
            get-response (http/get site get-request)
            body (get-response :body)]

        post-response => (contains {:status 302})
        (:headers post-response) => (contains {"location" "/"})
        get-response => (contains {:status 200})
        body => #"Thanks"
        body =not=> #"form"))