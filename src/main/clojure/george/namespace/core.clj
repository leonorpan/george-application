;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.namespace.core
  (:require
    [clojure.pprint :refer [pprint pp]]
    [george.javafx :as fx]))




(defn- namespaces []
  (all-ns))


(defn- scene-root []
  (let [
        root (fx/group)
        nss (namespaces)]

    (loop [ns nss cnt 0]
      (when (next nss))
      (fx/add root (fx/text (str (first ns))))
      (recur (next nss) (inc cnt)))

    root))

(defn -main [& args]
  (fx/later
    (fx/stage
      :title "RT"
      :size [600 600]
      :scene (fx/scene (scene-root)))))



;;; DEV ;;;

;(println "WARNING: Running george.namespace.core/-main") (-main)
