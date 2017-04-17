;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.example.arcclocks
    "A Clojure implementation of
    https://jaxenter.com/tutorial-a-glimpse-at-javafxs-canvas-api-105696.html
    "
    (:require [george.javafx :as fx]
              [clojure.string :as cs])
    (:import (javafx.animation AnimationTimer)
             (javafx.scene.canvas Canvas GraphicsContext)
             (javafx.scene.paint Color)
             (java.util Random Date)
             (javafx.scene.shape ArcType)
             (java.text SimpleDateFormat)
             (javafx.scene.text Font TextAlignment)
             (javafx.stage Stage)))

;; primitive math is faster
;(set! *unchecked-math* :warn-on-boxed)

;; avoiding reflection can make your code A LOT faster and animations smoother!
(set! *warn-on-reflection* true)


(def BLUE1 (Color/rgb 126, 166, 212, 0.6))
(def BLUE2 (Color/rgb 126, 166, 222, 0.5))
;(def BLUE3 (Color/rgb 130, 166, 230, 0.5))
(def GREEN1 (Color/rgb 130, 230, 166, 0.5))
(def RED1 (Color/rgb 230, 130, 166, 0.5))
(def DIAMETER (int  200))
(def SPACE 20)

(def ^Font Calibri40 (Font/font "Calibri" 40))
(def ^Font Calibri20 (Font/font "Calibri" 20))

(def MILLION 1000000)
(def ^SimpleDateFormat DATE_FORMAT (SimpleDateFormat. "yyyyMMddkkmmssX"))

(def ^Random random (Random.))

(defn- random-int-range [min max]
    (let [rang (+ (- max min) 1)]
        (+ (.nextInt random (Math/abs (int rang))) min)))


(defrecord ArcClock [long-piece arc-pieces])

(defrecord ArcPiece [x y d
                     startAngle arcExtent
                     strokeColor strokeWidth
                     pixelsToMove
                     clockwise
                     displayTimePerFrameMillis])


(defn- random-arc-piece [color radius]
    (let [diameter (random-int-range 60 (* radius 2))
          stroke-width (random-int-range 1 10)
          start-angle (random-int-range 1 270)
          extent-angle (random-int-range 10 (- 360 start-angle))
          millis (random-int-range 0 33)
          color (if color color BLUE1)
          clockwise (.nextBoolean random)]

        (->ArcPiece
            (- radius (/ diameter 2)) (- radius (/ diameter 2)) diameter
            start-angle extent-angle
            color stroke-width
            2
            clockwise
            millis)))



(defn- arc-clock [num-arcs long-piece-color many-piece-color]
    (->ArcClock
        (->ArcPiece
            0 0 DIAMETER
            45 240
            long-piece-color
            5 2
            false
            1000)
        (for [_ (range num-arcs)]
            (random-arc-piece many-piece-color (/ DIAMETER 2)))))



(defn- calculate-start-angle [clockwise startAngle pixelsToMove]
    (if clockwise
      (rem (- startAngle pixelsToMove) -360)
      (rem (+ startAngle pixelsToMove) 360)))


(defn- update-piece [piece now]
    (let [
          startTime
          (if-let [t (:startTime piece)] t now)

          displayTimePerFrameNano
          (if-let [n (:displayTimePerFrameNano piece)]
              n
              (* (:displayTimePerFrameMillis piece) MILLION))

          elapsed (- now startTime)

          startAngle (:startAngle piece)

          [startAngle startTime]
          (if (> elapsed displayTimePerFrameNano)
              [(calculate-start-angle (:clockwise piece) startAngle (:pixelsToMove piece))
               nil]
              [startAngle startTime])]

        (assoc piece
            :displayTimePerFrameNano displayTimePerFrameNano
            :startTime startTime
            :startAngle startAngle)))


(defn- draw-piece
    [{:keys [strokeColor strokeWidth x y d startAngle arcExtent] :as piece}  gc]
    (let [sw strokeWidth  sw2 (/ sw 2)]  ;; compensate for thickness of stroke to avoid clipping
     (doto ^GraphicsContext gc
         (.setStroke strokeColor)
         (.setLineWidth strokeWidth)
         (.strokeArc (+ x sw2) (+ y sw2) (- d sw) (- d sw) startAngle arcExtent ArcType/OPEN))))


(defn- update-clock [arc-clock now]
    (let [long-piece (update-piece (:long-piece arc-clock) now)
          arc-pieces (map #(update-piece % now) (:arc-pieces arc-clock))]
        (assoc arc-clock :long-piece long-piece :arc-pieces arc-pieces)))


(defn- draw-clock [{:keys [long-piece arc-pieces]} ^GraphicsContext gc]
    (let [dts (.format DATE_FORMAT (Date.))
          radius (/ DIAMETER 2)
          d-40 (- DIAMETER 40)]
        (draw-piece long-piece gc)
        (doseq [p arc-pieces] (draw-piece p gc))
        (doto gc
            (.setFont Calibri40)
            (.setTextAlign TextAlignment/CENTER)
            (.setFill Color/WHITE)
            (.fillText ^String (subs dts 8 10) radius (+ radius 18))
            (.setFont Calibri20)
            (.fillText ^String (format "%s  (%s)" (subs dts 10 12) (subs dts 14)) d-40 (- radius 40))
            (.fillText (subs dts 12 14) d-40 d-40))))





(defn- animation-timer [ clock-atoms ^GraphicsContext gc ^Stage stage]
    (proxy [AnimationTimer] []
        (handle [now]
            ;(time (do
                (doseq [x clock-atoms]
                    (swap! x update-clock now))

                (.clearRect gc 0 0 (.getWidth stage) (.getHeight stage))
                (.save gc)

                (doseq [x clock-atoms]
                    (draw-clock @x gc)
                    (.translate gc (+ DIAMETER SPACE) 0))

                (.restore gc))))
            ;))



(defn -main [& _]
        (let [
              canvas ^Canvas (Canvas.)
              gc (.getGraphicsContext2D canvas)

              three-clock-atoms
              (mapv (fn [[c1 c2]] (atom (arc-clock 20 c1 c2)))
                    (list [BLUE1 BLUE2] [BLUE1 GREEN1] [BLUE1 RED1]))

              stage
               (fx/now
                  (fx/stage
                      :title "ArcClocks"
                      :resizable false
                      :scene (fx/scene (fx/borderpane :center canvas :insets SPACE)
                                       :size [(+ (* 3 DIAMETER) (* 4 SPACE))
                                              (+ DIAMETER (* 2 SPACE))]
                                       :fill Color/BLACK)))



              timer
              ^AnimationTimer (animation-timer three-clock-atoms gc stage)]

            (doto canvas
                (-> .widthProperty (.bind (.widthProperty ^Stage stage)))
                (-> .heightProperty (.bind (.heightProperty ^Stage stage))))


            (.setOnCloseRequest ^Stage stage (fx/event-handler (.stop timer)))
            (.start timer)))



;;; DEV ;;;
;(println "WARNING: Running george.example.arcclock/-main" (-main))
