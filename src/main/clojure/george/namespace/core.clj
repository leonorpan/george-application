(ns
  ^{:author "Terje Dahl"}
  george.namespace.core
  (:require
    [clojure.pprint :refer [pprint pp]]
    [george.javafx.core :as fx]))




(defn- namespaces []
  (all-ns))


(defn- scene-root []
  (let [
        root (fx/group)
        nss (namespaces)]

    (loop [ns nss cnt 0]
      (when (next nss))
      (fx/add root (fx/text (str (first ns))))
      (recur (next nss)))

    root))

(defn -main [& args]
  (fx/later
    (fx/stage
      :title "RT"
      :size [600 600]
      :scene (fx/scene (scene-root)))))



;;; DEV ;;;

;(println "WARNING: Running george.namespace.core/-main") (-main)
