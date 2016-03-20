(ns example.app)

(defn -main [& args]
    (println "Clojure: (example.app/-main ...): Hello World!")
    (println "   args:" (apply str (interpose " " args)))
    )

(defn hello []
    "Hello from example.app/hello")
