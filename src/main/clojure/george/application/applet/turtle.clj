;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

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