(ns circle.db.analysis
  "Utility functions for the DB"
  (:require [clojure.string :as str])
  (:require [somnium.congomongo :as mongo])
  (:require [clojure.set :as set]))

(defn spec-distinct-lines [spec]
  (-> spec
      (select-keys [:setup :dependencies :compile :test])
      (vals)
      (->>
       (mapcat #(str/split % #"\r\n"))
       (filter #(> (.length %) 0))
       (map (fn [s] {s 1})))))

(defn all-spec-lines []
  (->> (mongo/fetch :projects)
       (mapcat spec-distinct-lines)))

(defn spec-histogram
  "returns a histogram of unique lines in the spec files"
  []
  (->> (all-spec-lines)
       (apply merge-with +)
       (seq)
       (sort-by second)
       (reverse)))

(defn projects-with
  "Returns a list of project urls that contain spec lines that match the regex"
  [re]
  (->> (mongo/fetch :projects)
       (map (fn [project]
              {:project (-> project :vcs_url)
               :lines (->> project
                           (spec-distinct-lines)
                           (mapcat keys)
                           (filter #(re-find re %)))}))
       (filter (fn [{:keys [project lines]}]
                 (->> lines
                      (seq)
                      (boolean))))))

;; (defn num-pure-inferred-projects
;;   "Returns a list of projects purely inferred, and several builds"
;;   []
;;   (let [
;;         project-ids (->> (mongo/fetch :projects)
;;                           (map :_id)
;;                           (into #{}))
;;         pure-inferred (set/difference project-ids spec-project-ids)]
;;     (println (count pure-inferred) (first pure-inferred))
;;     (->> pure-inferred
;;          (map #(mongo/fetch-one :projects :where {:_id %}))
;;          (map :vcs_url))))