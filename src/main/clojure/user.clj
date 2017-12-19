;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "Load some useful things to make them available globally."}
  user
  (:require
    ;; Need to be required to allow potemkin to "see" them.
    [clojure.pprint]
    [clojure.repl]

    [potemkin :refer [import-vars import-macro import-fn]]))

;(println "user.clj ...")

(import-vars [clojure.pprint pprint])
(import-macro clojure.repl/doc)
(import-macro clojure.repl/dir)


(defn help
  "Prints some basic help for this namespace."
  []
  (println "# Help for namespace: user

This namespace has the following items pre-loaded:
  pprint   Prints any single data object in a structured manner (to *out*).
  doc      Prints the documentation for any var in any namespace. Also the namespace itself.
  dir      Prints a sorted list of all public vars in a namespace.

To view their documentation, do:  (user/doc <function-or-macro>)
Ex.: (user/doc pprint)
"))


(println "Namespace 'user' has pre-loaded some useful vars.  For more info, do: (user/help)")