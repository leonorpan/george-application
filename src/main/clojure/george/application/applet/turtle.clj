(ns george.application.applet.turtle
  (:require
    [george.application.environment :as ide]))


(defn info []
  {:george.application.applet/name "Turtle Geometry"
   :george.application.applet/description "An interactive development environment for 'turtle geometry'"
   :george.application.applet/main '-main})


(defn -main []
  (println "george.application.applet.turtle/-main")
  (ide/-main :turtle))