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

