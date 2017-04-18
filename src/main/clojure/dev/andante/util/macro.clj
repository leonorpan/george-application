;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns dev.andante.util.macro)



(defmacro defn+
    "same as Clojure's defn, yielding public def"
    [name & decls]
    (list* `defn (with-meta name (assoc (meta name) :public true)) decls))

#_(defmacro defn
    "same as Clojure's defn-, yielding non-public def"
    [name & decls]
    (list* `defn (with-meta name (assoc (meta name) :private true)) decls))

(defn mac1
    "same as `macroexpand-1`"
    [form]
    (. clojure.lang.Compiler (macroexpand1 form)))

#_(let [  ex1 (mac1 '(defn f1 [] (println "Hello me.")))
        ex2 (mac1 '(defn+ f2 [] (println "Hello World!"))) ]
    (defn f1 [] (println "Hello me."))
    (defn+ f2 [] (println "Hello World!"))
    (prn ex1) (prn (meta #'f1)) (f1)
    (prn ex2) (prn (meta #'f2)) (f2) )

