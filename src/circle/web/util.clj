(ns circle.web.util
  (:use [hiccup.form-helpers :only (submit-button hidden-field)]))

(defn post-link
  "makes an <a href> that performs a POST. Creates a hidden form, and a link with javascript that calls form.submit()
   
   optional key: hidden-input, a map. Each pair will become an input type=hidden on the form"
  ([url content]
     (post-link nil url content nil))
  ([link-attrs url content]
     (post-link link-attrs url content nil))
  ([link-attrs url content {:keys [form-name hidden-input]}]
     (assert (or (nil? link-attrs) (map? link-attrs)))
     (assert (string? url))
     (let [form-name (if form-name
                       (gensym form-name)
                       (gensym))]
       (hiccup.core/html 
        [:form {:name form-name :method "POST" :action url :style "display:none"}
         (map (fn [[key val]]
                (hidden-field key val)) hidden-input)
         (submit-button "submit")]
        [:a (merge link-attrs {:href (format "javascript:document.%s.submit()" form-name)}) content]))))