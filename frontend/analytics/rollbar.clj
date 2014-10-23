(ns frontend.analytics.rollbar)

;; n.b. that we can't use utils here because we want to report errors to rollbar in swallow-errors

;; Making this a macro so that we can call it form the swallow-errors macro
(defmacro push [& args]
  `(try
     (js/Rollbar.log ~@args)
     (catch :default e#
       (js/console.log e#))))
