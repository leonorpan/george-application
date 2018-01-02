;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.applet.turtle-ide
  (:require
    [george.applet :refer [->AppletInfo]]

    [george.javafx :as fx]
    [george.javafx.java :as fxj]

    [george.application
     [environment :as ide]]

    [george.application.turtle.turtle :as turtle]
    [george.application.ui.styled :as styled])

  (:import
    [javafx.animation Timeline]
    [javafx.scene.paint Color]))


(defn icon
  "Returns a node of the given dimension - to be place in the middle fo the launcher button."
  [w h]
  (let [x1 10
        y1 (/ h 2)
        x2 (- w 10)
        y2 (/ h 2)

        background-rect
        (fx/rectangle :size [w h] :fill Color/TRANSPARENT)
        trtl
        (doto (turtle/turtle-polygon)
              (fx/set-translate-XY [x1 y1]))
        t
        (doto (fx/simple-timeline
                1000
                nil
                [(.translateXProperty trtl) x2]
                [(.translateYProperty trtl) y2])
          (.setCycleCount Timeline/INDEFINITE)
          (.setAutoReverse true))

        g
        (doto
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


(defn label []
  "Turtle Geometry")


(defn description []
  "Turtle Geometry IDE
(Interactive Development Environment)")


(defn main []
  (ide/ide-root :turtle))

(defn dispose []
  (ide/ide-root-dispose :turtle)
  (styled/heading (format "%s has been disposed" (label))))


(defn applet-info []
  (->AppletInfo
    'label
    'description
    'icon
    'main
    'dispose))
