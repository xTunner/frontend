(ns circle.languages
  "programming language detection. Mainly used for determining what to infer next"
  (:use [clojure.core.incubator :only (-?>)])
  (:require [circle.util.fs :as cfs])
  (:require fs)
  (:require [clojure.string :as str])
  (:use [circle.util.seq :only (find-first)]))

(def file-type-map {#".*\.clj" :clojure
                    #".*\.rb" :ruby
                    #".*\.rake" :rake
                    #"^Rakefile$" :rake
                    #"^Gemfile$" :bundler
                    #".*\.py" :python
                    #".*\.pyc" :python
                    #".*\.js" :javascript
                    #".*\.[ch]$" "C"})

(defn detect-language [path]
  (let [basename (fs/basename path)]
    (-> (find-first (fn [[re type]]
                      (re-find re basename)) file-type-map)
        (second)
        ((fn [extension]
           (cond
            extension extension
            (seq (fs/extension basename)) (-?> basename (fs/extension) (str/replace #"^\." "") (str/lower-case) (keyword))
            :else (-?> basename (str/lower-case) (keyword))))))))

(defn file-info [filename]
  "Returns a map of information about a file, like primary language, line count, etc"
  ;; (println "file-info:" filename)
  {:loc (cfs/line-count filename)
   :file-count 1
   :language (detect-language filename)})

(defn remove-git [files]
  (remove #(re-find #"/.git/" (str %)) files))

(def remove-files #{#".pdf$"
                    #".png$"
                    #".gif$"
                    #".jpg$"}) ;; don't count any file that matches these regex
(defn remove-binary
  "Don't"
  [files]
  (->> files
       (remove (fn [f]
                 (some #(re-find % (str f)) remove-files)))))

(defn lang-histogram
  "Returns a breakdown of language usage for all files in a given directory"
  [dir]
  (->> dir
       (cfs/all-files)
       (remove-git)
       (remove-binary)
       (map file-info)
       (reduce (fn [data {:keys [language]
                          :as file-data}]
                 (update-in data [language] #(merge-with + %1 %2 ) (dissoc file-data :language))) {})))

(defn all-repo-languages []
  (->> (fs/listdir "repos")
       (map #(fs/join "repos" %))
       (map (fn [dir]
              {:dir dir
               :data (circle.languages/lang-histogram dir)}))
       (take 3)))