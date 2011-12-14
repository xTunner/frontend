(ns circle.backend.project.rails
  (:require [circle.backend.nodes :as nodes])
  (:use [circle.backend.build :only (build)])
  (:use [circle.backend.action.vcs :only (checkout)])
  (:use [circle.backend.action.bash :only (bash)]))

;; (def rails-build (build {:project_name "rails"
;;                         :build_num 1
;;                         :actions [(checkout "git://github.com/rails/rails.git")
;;                                   (bash [(bundle install)])
;;                                   (bash [(export "RUBYOPT=rubygems")
;;                                          (bundle exec rake test)])]
;;                         :group nodes/builder-group}))