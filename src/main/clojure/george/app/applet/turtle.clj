(ns george.app.applet.turtle
  (:require
    [george.app.turtle.environment :as turtle]))


(defn info []
  {:george.app.applet/name "Turtle Geometry"
   :george.app.applet/description "An interactive development environment for 'turtle geometry'"
   :george.app.applet/main '-main})


(defn -main []
  (println "george.app.applet.turtle/-main")
  (turtle/-main))