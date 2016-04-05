(ns george.example.app)

(defn -main [& args]
    (println "Clojure: (george.example.app/-main ...): Hello World!")
    (println "   args:"
             (if (empty? args)
                 "NO ARGS"
                 (apply str (interpose " " args))
             )
    ))

(defn hello []
    "Hello from george.example.app/hello")
