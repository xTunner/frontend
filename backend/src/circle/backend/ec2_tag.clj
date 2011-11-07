(ns circle.backend.ec2-tag
  (:use [circle.backend.ec2 :only (with-ec2-client)])
  (:import (com.amazonaws.services.ec2.model Tag
                                             CreateTagsRequest)))

;; tools for adding tags (metadata) to EC2 instances

;; http://docs.amazonwebservices.com/AWSEC2/latest/UserGuide/index.html?Using_Tags.html


(defn add-tags
  "Adds tags to instances. instance-ids is a seq of strings, each an
  instance-id (or ID of something that can be tagged). tags is a map."
  [instance-ids tags]
  (with-ec2-client client
    (let [request (CreateTagsRequest. instance-ids (for [[k v] tags]
                                                     (Tag. (name k) v)))]
      (.createTags client request))))


(defn describe-tags
  "returns all tags on all instances"
  ([]
     (with-ec2-client client
       (let [result (.describeTags client)]
         (map bean (.getTags result))))))

