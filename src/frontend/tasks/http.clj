(ns frontend.tasks.http
  (:require [frontend.stefon]
            [frontend.stefon-with-sourcemaps :as stefon-with-sourcemaps]
            [frontend.less :as less]
            [frontend.util.docs :as doc-utils]
            [fs]
            [stefon.core :as stefon]
            [stefon.manifest]
            [cheshire.core :as json]
            [org.httpkit.client :as http]))

(def stefon-options {:asset-roots frontend.stefon/asset-roots
                     :precompiles frontend.stefon/precompiles
                     :serving-root "resources/public"
                     :manifest-file "resources/public/assets/stefon-manifest.json"
                     :mode :production})

(defn update-hosted-scripts [scripts]
  (loop [tries 0]
    (let [result (try
                   (doseq [script scripts]
                     (println (format "Updating %s" script))
                     (let [response @(http/get (:url script) {:as :text})]
                       (assert (= (:status response) 200))
                       (spit (str "resources/assets/" (:path script)) (:body response))))
                   :success
                   (catch Exception e
                     (if (> 3 tries)
                       :retry
                       :fail)))]
      (condp = result
        :success :success
        :retry (recur (inc tries))
        (throw (Exception. "Couldn't compile hosted scripts"))))))

(defn generate-doc-manifest []
  (let [doc-root "resources/assets/docs"
        json (-> doc-root
                 doc-utils/read-doc-manifest
                 (json/generate-string {:pretty true}))]
    (spit (fs/join doc-root "manifest.json") json)))

(defn precompile-assets []
  (generate-doc-manifest)
  (update-hosted-scripts frontend.stefon/hosted-scripts)
  (less/compile!)
  (println (format "Stefon options: %s" stefon-options))
  (stefon-with-sourcemaps/register)
  (stefon/precompile stefon-options))
