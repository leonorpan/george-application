(ns george.example.rcanvas
    ;; inspired by http://dlsc.com/2014/04/10/javafx-tip-1-resizable-canvas/
    ;; but it seems to work even without the overrides !?!
    (:require [george.javafx :as fx])
    (:import (javafx.scene.paint Color)
             (javafx.scene.canvas Canvas)))



(defn- draw-red-x-box [canvas]
    (let [w (.getWidth canvas)
          h (.getHeight canvas)
          gc (.getGraphicsContext2D canvas)]
        (doto gc
            (.clearRect 0 0 w h)
            (.setStroke Color/RED)
            (.strokeRect 1 1 (- w 2) (- h 2))
            (.strokeLine 0 0 w h)
            (.strokeLine 0 h w 0))))



(defn- resizable-canvas []
    (let [rc (proxy [Canvas] [])]
                 ;(isResizable [] true)
                 ;(prefWidth [h] (.getWidth this))
                 ;(prefHeight [w] (.getHeight this)))]
        (doto rc
            (-> .widthProperty (.addListener (fx/changelistener [_ _ _ _] (draw-red-x-box rc))))
            (-> .heightProperty (.addListener (fx/changelistener [_ _ _ _] (draw-red-x-box rc)))))))



(defn resizable-canvas-stage []

  (let [
        canvas (resizable-canvas)
        _ (draw-red-x-box canvas)
        pane (fx/stackpane canvas)]

    (doto canvas
      (-> .widthProperty (.bind (.widthProperty pane)))
      (-> .heightProperty (.bind (.heightProperty pane))))

    (fx/later
       (fx/stage
          :title "Resizable Canvas"
          :scene (fx/scene pane)))))




;;; DEV ;;;
;(println "WARNING: Running george.turtle.turtle/resizable-canvas-stage" (resizable-canvas-stage))

