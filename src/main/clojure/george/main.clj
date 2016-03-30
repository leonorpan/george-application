(ns george.main
  (:gen-class))


(defn -main [& args]
  (println "loading ..." )
  (clojure.main/main "-m" "george.launcher")
  )