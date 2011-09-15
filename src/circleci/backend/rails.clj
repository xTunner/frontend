(ns circleci.backend.rails)

;; code for building rails

(defn go []
  ;; startup node
  (pallet.core/lift ~group
                    :compute (pallet.compute/service :aws)
                    :phase (pallet.resource/phase (pallet.action.exec-script/exec-script
                                                   (git clone "git://github.com/rails/rails.git")
                                                   (cd "rails")
                                                   (sudo bundler install) ;; sudo, because building gems as sudoer makes them own ~/.gems
                                                   ))))