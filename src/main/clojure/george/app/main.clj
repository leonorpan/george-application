(ns george.app.main)




(defn -main [& args]
  (println "george.app.main/-main"
           (if (empty? args)
             "(no args)"
             (str " args: " (apply str (interpose " " args))))))
