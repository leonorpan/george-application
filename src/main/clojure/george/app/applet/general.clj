(ns george.app.applet.general
  (:require [george.core.core :as general]))


(defn info []
  {:george.app.applet/name "General Environment"
   :george.app.applet/description "A general interactive Clojure development environment"
   :george.app.applet/main '-main})


(defn -main []
  (println "george.app.applet.general/-main")
  (general/-main))


