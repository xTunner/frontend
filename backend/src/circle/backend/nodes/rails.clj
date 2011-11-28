(ns circle.backend.nodes.rails)

;; default node for all rails projects

(def rails-node
  {:ami "ami-a5c70ecc"
   :instance-type "m1.small"
   :username "ubuntu"
   :security-groups ["www"]
   
   ;;following only necessary for deployment
   ;; availability-zone: "us-east-1a" ;;customers *could* specify this, probably shouldn't care.
   ;; public-key: "backend/www.id_rsa.pub"
   ;; private-key: "backend/www.id_rsa"
   
})