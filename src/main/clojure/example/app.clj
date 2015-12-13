(ns example.app)

(defn -main [args]
  (println "Clojure: example.app/-main: Hello World!")
  (println "Java called Clojure function with args:"
      (apply str (interpose " " args))))

(defn hello []
    "Hello from example.app/hello")
