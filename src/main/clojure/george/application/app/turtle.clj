;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.app.turtle
  (:require

    [george.application.environment :as ide]
    [george.javafx :as fx]
    [george.application.turtle.turtle :as turtle]
    [george.javafx.java :as fxj])
  (:import (javafx.animation Timeline)
           (javafx.scene.paint Color)
           (javafx.scene.control Button ContentDisplay)
           (javafx.scene Node)
           (javafx.geometry Pos)
           (javafx.scene.image Image ImageView)
           (javafx.scene.text TextAlignment)))


(defn icon
  "Returns a node of the given dimension - to be place in the middle fo the launcher button."
  [w h]
  (let [x1 10 y1 (/ h 2)
        x2 (- w 10) y2 (/ h 2)

        background-rect (fx/rectangle :size [w h] :fill Color/TRANSPARENT)
        trtl (doto (turtle/turtle-polygon)
               (fx/set-translate-XY [x1 y1]))
        t (doto
            (fx/simple-timeline
              1000
              nil
              [(.translateXProperty trtl) x2]
              [(.translateYProperty trtl) y2])
            (.setCycleCount Timeline/INDEFINITE)
            (.setAutoReverse true))

        g (doto
            (fx/group
              background-rect
              (doto (fx/line :x1 x1
                             :y1 y1
                             :x2 x2
                             :y2 y2
                             :color Color/DODGERBLUE)
                (-> .getStrokeDashArray (.setAll (fxj/vargs 5. 5.))))
              trtl)

            (.setOnMouseEntered (fx/event-handler (.play t)))
            (.setOnMouseExited (fx/event-handler (.pause t))))]
    g))





(defn main []
  (println "george.application.app.turtle/main")
  (ide/-main :turtle))



(defn app-info []
  {:george.application.app/name "Turtle Geometry"
   :george.application.app/description "Turtle Geometry \nIDE (Interactive Development Environment)"
   :george.application.app/icon-fn 'icon
   :george.application.app/main-fn 'main})
