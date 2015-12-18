(ns example.app)

(defn -main [args]
  (println "Clojure: example.app/-main: Hello World!")
  (println "   args from Java:"
      (apply str (interpose " " args))))

(defn hello []
    "Hello from example.app/hello")
