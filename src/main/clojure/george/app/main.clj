(ns george.app.main
  (:require [george.app.applets :as applets]))



(defn -main [& args]
  (println "george.app.main/-main"
           (if (empty? args)
             "(no args)"
             (str " args: " (apply str (interpose " " args)))))

  (let [applet-info-seq (applets/load-applets)]
    (doseq [a (doall applet-info-seq)]
      (println a))))
