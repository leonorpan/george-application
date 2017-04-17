;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.core.visual
  (:require
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.javafx.util :as fxu]
    [george.javafx.3d :as fx3d]
    [george.application.turtle.turtle3D :as tr])


  (:import (javafx.scene DepthTest PerspectiveCamera CacheHint)
           (javafx.scene.transform Translate Rotate)
           (javafx.scene.paint Color PhongMaterial)
           (javafx.scene.shape Sphere)
           (javafx.geometry Point3D)))





(defn- create-origo []
  (fx3d/sphere [0 0] 2 Color/GRAY))


(defn- controls []
    (let [
          pane
          (fx/borderpane
              :center (fx/textarea :font (fx/SourceCodePro "Regular" 16))
              :bottom (fx/button "Do" :onaction #(println "do")))]

        (doto pane
            (.setPrefWidth 300.)
            (.setPrefHeight 200.))

        pane))


(defn- build-camera []
  (let [

        camera
        (doto (PerspectiveCamera. true)
          (.setNearClip 0.1)
          (.setFarClip 1000))

        t (Translate. 25 -105 -240);(Translate. 50 -150 -250)
        ry (Rotate. -4 Rotate/Y_AXIS)  ;; pan
        rx (Rotate. -20 Rotate/X_AXIS) ;; tilt

        inner-camera-group  ;; rotates the camera in all directions, and holds the camera
        (doto (fx/group camera)
          (-> .getTransforms (.setAll [ry rx])))

        outer-camera-group  ;; moved the camera around, and holds the rotate-group
        (doto (fx/group inner-camera-group)
          (-> .getTransforms (.setAll [t])))]

    {:t t :ry ry :rx rx :c camera :n outer-camera-group}))



(defn- create-stage []
  (fx/now
    (let [
          world (fx/group)
          {:keys [c n] :as camera} (build-camera)
          root (doto (fx/group world n) (.setDepthTest DepthTest/ENABLE))
          scene (fx/scene root :size [600 600] :fill fx/WHITESMOKE :depthbuffer true)
                          ;:antialiasing nil

          stage
          (fx/now (fx/stage
                    :title "Core - 3D"
                    :sizetoscene true
                    :scene scene))

          origo (create-origo)
          state {:camera camera :origo origo}]


      (.setCamera scene c)

      (tr/set-keyhandler scene state)

      #_(doto world
         (-> .getTransforms (.setAll
                              [
                              ;(Translate. 0 0 0)
                               (Rotate. 90 0 0 0 Rotate/X_AXIS)])))



      (fx/add world origo)
      (fx/add world
              (doto (fx/button "Button!"
                               :onaction #(println "Button!"))
                  (.setCache true)
                  ;(.setCacheHint CacheHint/SCALE_AND_ROTATE)
                  (.setCacheHint CacheHint/QUALITY)))


      (fx/add world
              (doto (fx/button "Button!!"
                               :onaction #(println "Button!!"))
                  (.setCache true)
                  (fx3d/set-translate [-20 0 500])))


      (fx/add world
              (doto (controls)
                  (.setCache true)
                  (fx3d/set-translate [-20 0 100])))


      (.setUserData stage {})

      stage)))




;;;; main ;;;;

(defn -main
  "Launches an input-stage as a stand-alone application."
  [& args]
  (fx/later (create-stage)))


;;; DEV ;;;

;(println "WARNING: Running george.core.visual/-main" (-main))
