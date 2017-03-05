(ns george.application.applet.ide
  (:require
    [george.application.environment :as ide]))


(defn info []
  {:george.application.applet/name "IDE"
   :george.application.applet/description "An interactive development environment Clojure"
   :george.application.applet/main '-main})


(defn -main []
  (println "george.application.applet.ide/-main")
  (ide/-main :ide))