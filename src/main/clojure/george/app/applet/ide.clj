(ns george.app.applet.ide
  (:require
    [george.app.environment :as ide] :reload))


(defn info []
  {:george.app.applet/name "IDE"
   :george.app.applet/description "An interactive development environment Clojure"
   :george.app.applet/main '-main})


(defn -main []
  (println "george.app.applet.ide/-main")
  (ide/-main :ide))