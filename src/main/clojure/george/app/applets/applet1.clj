(ns george.app.applets.applet1)


(defn info []
  {:george.app.applet/name "Applet 1"
   :george.app.applet/description "A very exiting applet"
   :george.app.applet/main 'main})


(defn main []
  (println "george.app.applets.applet1/main"))