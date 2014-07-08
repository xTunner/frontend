(ns frontend.components.docs.clojure-12)

(def article
  {:title "java.lang.NoSuchMethodError: clojure.lang.KeywordLookupSite"
   :last-updated "Feb 2, 2013"
   :url :clojure-12
   :content [:div
             [:p
              "This is caused by using jars that were AOT compiled against1.2.1 or lower, with Clojure 1.3 or higher. The most frequentoffender is clojure-contrib, which is not compatible withClojure 1.3+."]
             [:p
              "If this works on your (OSX) machine but fails on CircleCI, it alsomeans you are running two different clojure versions at the sametime."]
             [:p
              "To determine whether you are loading two different clojure.jars,look in lib/, and lib/dev/. If you are, fix this issue beforetrying to upgrade dependencies. Typically, you'll need to addexclusions to the dependency that requires clojure 1.2. Thedependency in your project.clj will look like:"]
             [:code "[org.foo/bar \\1.0.0\\ :exclusions [org.clojure/clojure]]"]
             [:p
              "To find jars that depend on clojure 1.2, you can use the command"]
             [:code "lein pom && mvn dependency:tree"]
             [:p
              "To fix the KeywordLookupSite error, look at your dependencies,and upgrade them as appropriate. The easiest way to determinewhether they work in clojure 1.3 is to a repl via"
              [:code "lein repl"]
              "or"
              [:code "lein swank"]
              "and start requiring libraries until you find one that doesn't compile."]]})
