(ns circle.util.test-chdir
  (:use midje.sweet)
  (:require fs)
  (:use circle.util.chdir)
  (:require circle.init))

(circle.init/init) ; ensure we're in the right directory

(fact "chdir and slurp work together"
  (let [old (fs/normpath (fs/cwd))
        new (fs/join "backend" "src" "circle") ; this assumes the starting working directory is the
                                               ; repo root, not backend/
        succ (chdir new)
        contents (slurp "init.clj")
        succ2 (chdir old)]
    succ => true
    succ2 => true
    contents =not=> empty?)) ; and of course, that it works.
