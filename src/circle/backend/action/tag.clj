(ns circle.backend.action.tag
  (:require [circle.backend.action :as action])
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.ec2-tag :as tag]))

(defn tag-revision []
  "tag all ec2 instances with the code revision"
  (action/action 
   :name (format "tag revision")
   :act-fn (fn [context]
             (tag/add-tags (nodes/group-instance-ids (-> context :build :group))
                           {:rev (-> context :build :vcs-revision)})
             {:success true})))