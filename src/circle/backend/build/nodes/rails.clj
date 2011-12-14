(ns circle.backend.build.nodes.rails
  "Default build node spec for rails projects")

(def default-rails-node
  {:ami "ami-a5c70ecc" ;; current circle image, fixme
   :instance-type "m1.small"
   :username "ubuntu"
   :availability-zone "us-east-1a"})