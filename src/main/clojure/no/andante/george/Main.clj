(ns no.andante.george.Main

  (:gen-class
    :main true
    :name no.andante.george.Main
    :extends javafx.application.Application)

  (:require [george.application.applet-loader :as applets]
            [george.application.launcher :as launcher]))




(defn -start [this ^javafx.stage.Stage stage]
  (println "Main.start() ...")
  (println "params are:" (-> this .getParameters .getRaw seq))

  ;(println "args are:"
  ;         (if (empty? args)
  ;           "(no args)"
  ;           (str " args: " (apply str (interpose " " args))))))

  ;(let [applet-info-seq (applets/load-applets)]
  ;     (doseq [a (doall applet-info-seq)] (println a)))

  (launcher/start stage))


(defn -stop [this]
  (println "stop called"))


(defn -main
  "Launch the JavaFX Application using class no.andante.george.Main"
  [& args]
  (javafx.application.Application/launch
    no.andante.george.Main
    (into-array String args)))
