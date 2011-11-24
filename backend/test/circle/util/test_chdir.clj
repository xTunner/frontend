(ns circle.util.test-chdir
  (:use midje.sweet)
  (:require fs)
  (:use circle.util.chdir))

(fact "chdir and slurp work together"
  (let [old (fs/normpath (fs/cwd))
        new (fs/join "src" "circle") ; this assumes the starting working directory is backend/, so
                                     ; if we start to run tests from within JRuby, this needs to
                                     ; change.
        succ (chdir new)
        contents (slurp "init.clj")
        succ2 (chdir old)]
    succ => true
    succ2 => true
    contents =not=> empty?)) ; and of course, that it works.
