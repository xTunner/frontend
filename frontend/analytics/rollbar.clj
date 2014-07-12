(ns frontend.analytics.rollbar)

;; n.b. that we can't use utils here because we want to report errors to rollbar in swallow-errors

;; Making this a macro so that we can call it form the swallow-errors macro
(defmacro push [& args]
  `(try
     (let [rollbar# (aget js/window "_rollbar")
           push# (aget rollbar# "push")]
       (.call push# rollbar# ~@args))
     (catch :default e#
       (js/console.log e#))))
