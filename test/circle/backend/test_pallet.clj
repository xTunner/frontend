(ns circle.backend.test-pallet
  (:use midje.sweet)
  (:require [circle.backend.pallet]))

(fact "flags->rvmrc works"
  (circle.backend.pallet/flags->rvmrc {:a 4 :b 6 :f "some string"})
  => "export a=4\nexport b=6\nexport f=\"some string\"\n")