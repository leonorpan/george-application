(ns george.app.main
  (:require
    [george.app.applet-loader :as applets] :reload
    [george.app.launcher :as launcher] :reload))


(defn -main [& args]
  (println "george.app.main/-main" (if (empty? args) "(no args)" (str " args: " (apply str (interpose " " args)))))

  (let [applet-info-seq (applets/load-applets)]

    (doseq [a (doall applet-info-seq)] (println a))

    (launcher/-main)))



;; DEV
(println "WARNING: Running george.app.main/-main" (-main))