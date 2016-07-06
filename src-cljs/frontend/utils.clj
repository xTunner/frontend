(ns frontend.utils
  (:require [cljs.analyzer :as cljs-ana]
            [cljs.analyzer.api :as cljs-ana-api]
            [sablono.core :as html]))

(defmacro inspect
  "prints the expression '<name> is <value>', and returns the value"
  [value]
  `(do
     (let [name# (quote ~value)
           result# ~value]
       (print (pr-str name#) "is" (pr-str result#))
       result#)))

(defmacro timing
  "Evaluates expr and prints the label and the time it took.
  Returns the value of expr."
  {:added "1.0"}
  [label expr]
  `(let [global-start# (or (aget js/window "__global_time")
                          (aset js/window "__global_time" (.getTime (js/Date.))))
         start# (.getTime (js/Date.))
         ret# ~expr
         global-time# (- (.getTime (js/Date.)) global-start#)]
     (aset js/window "__global_time" (.getTime (js/Date.)))
     (prn (str ~label " elapsed time: " (- (.getTime (js/Date.)) start#) " ms, " global-time# " ms since last"))
     ret#))

(defmacro swallow-errors
  "wraps errors in a try/catch statement, logging issues to the console
   and optionally rethrowing them if configured to do so."
  [& action]
  `(try
     (try ~@action
          (catch js/Error e#
            (merror e#)
            (when (:rethrow-errors? initial-query-map)
              (js/eval "debugger")
              (throw e#))))
     (catch :default e2#
       (merror e2#)
       (when (:rethrow-errors? initial-query-map)
         (js/eval "debugger")
         (throw e2#)))))

(defmacro defrender
  "defs a function which reifies an IRender component that only has a render
  function and splices the body into the render function"
  [name args & body]
  `(defn ~name ~args
     (reify
       om.core/IDisplayName
       (~'display-name [~'_] ~(str name))
       om.core/IRender
       (~'render [~'_] ~@body))))

(defmacro defrendermethod
  "defs a method which reifies an IRender component that only has a render
  function and splices the body into the render function"
  [multifn dispatch-val args & body]
  `(defmethod ~multifn ~dispatch-val ~args
     (reify
       om.core/IDisplayName
       (~'display-name [~'_] ~(str multifn " (" dispatch-val ")"))
       om.core/IRender
       (~'render [~'_] ~@body))))

(defmacro html [body]
  `(if-not (:render-colors? initial-query-map)
     (html/html ~body)
     (let [body# ~body]
       (try
         (let [[tag# & rest#] body#
               attrs# (if (map? (first rest#))
                        (first rest#)
                        {})
               rest# (if (map? (first rest#))
                       (rest rest#)
                       rest#)]
           (html/html (vec (concat [tag# (assoc-in attrs# [:style :border] (str "5px solid rgb("
                                                                                (rand-int 255)
                                                                                ","
                                                                                (rand-int 255)
                                                                                ","
                                                                                (rand-int 255)
                                                                                ")"))]
                                   rest#))))
         (catch :default e#
           (html/html body#))))))

(def ^:private component-name-symbol
  "A symbol which will be (lexically) bound to the current component name inside
  a component form."
  (gensym "component-name"))

(defmacro component
  "Assigns a component name (a data-component attribute) to a React element.
  body should be an expression which returns a React element (such as a call to
  sablono.core/html) and name should be the name of the function or React class
  that's rendering it. There's no way to programmatically find that name, so it
  needs to be passed to `component`, but `component` will verify at compile time
  that it refers to an actual Var, to ward against typos.

  It also sets the component name that `element` will use to build an element
  name.

  Examples:

  ;; Functional stateless component
  (defn fancy-button [on-click title]
    (component fancy-button
      (html [:button {:on-click on-click} title])))

  ;; Om Previous component
  (defn person [person-data owner]
    (reify
      om/IRender
      (render [_]
        (component person
          [:div
           [:.name (:name person-data)]
           [:.hair-color (:hair-color person-data)]]))))

  ;; Om Next component
  (defui Post
    static om/IQuery
    (query [this]
      [:title :author :content])
    Object
    (render [this]
      (let [{:keys [title author content]} (om/props)]
        (component Post
          (html
           [:article
            [:h1 title]
            [:h2 \"by \" author]
            [:div.body content]])))))"
  [name body]
  (assert (and (symbol? name)
               (nil? (namespace name)))
          (str "Component name should be given as an unqualified symbol, but was given as " (prn-str name)))
  (let [ns cljs-ana/*cljs-ns*
        full-name (str ns "/" name)]
    (assert (cljs-ana-api/ns-resolve ns name)
            (str "No such Var " full-name ". The component macro must be given the name of an existing Var (generally the Var within whose definition it is called)."))
    `(let [~component-name-symbol ~full-name]
       (component* ~full-name ~body))))

(defmacro element
  "Assigns an element name (a data-element attribute) to a React element.
  body should be an expression which returns a React element (such as a call to
  sablono.core/html) and element-name should be an unqualified keyword which is
  unique within the component.

  The element macro is used to give a component-namespaced identifier to a DOM
  node which is passed to another component as a param. Without this, the
  element's component's stylesheet would have no safe way to select the correct
  DOM node.

  Example:

  (ns example.core)

  (defn card [title content]
    (component card
      (html
       [:div
        [:.title title]
        [:.body content]])))

  (defn library-info-card [books]
    (component library-info-card
      (card
       \"Library Info\"
       (element :card-content
         (html
          [:.stats \"The library contains \" (count books) \" books.\"]
          [:ul.books
           (for [book books]
             [:li
              [:.title (:title book)]
              [:.author (:author book)]])])))))

  Without using `element`, there would be no safe way to select and style the
  book's `.title`. Consider the problem with these attempts:

      [data-component='example.core/library-info-card'] .title

  This also matches the title of the card itself.

      [data-component='example.core/library-info-card']
        > div > .body > .books > li > .title

  This requires knowledge of the DOM structure of a `card`. If `card`'s
  definition changes, the selector will break.

  There's one other big reason we can't use either of those selectors: there's
  no DOM element with that `data-component` value. The topmost DOM node in the
  `library-info-card` component isn't rendered directly by `library-info-card`,
  it's rendered by `card`. That node's `data-component` is `example.core/card`.
  This doesn't apply in all cases, but it applies to any component which renders
  another component at the top of its tree.

  All this is why we need the `element` macro. With it, we can now select:

      [data-element='example.core/library-info-card/card-content']
        > .books > li > .title

  That will always match exactly the node we mean."
  [element-name body]
  (assert (and (keyword? element-name)
               (nil? (namespace element-name)))
          (str "Element name should be given as an unqualified keyword, but was given as " (prn-str element-name)))
  `(element* ~(name element-name) ~component-name-symbol ~body))
