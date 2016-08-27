(ns george.app.applets.applet2)

(defn info []
  {:george.app.applet/name "Applet 2"
   :george.app.applet/description "Another fun applet"
   :george.app.applet/main '-main})


(defn -main []
  (println "george.app.applets.applet2/-main"))