(ns
  ^{:author "Terje Dahl"}
  george.turtle.core

  (:require
      [george.javafx.core :as fx]
      :reload
      [george.javafx.util :as fxu]
      :reload
    )

    (:import (javafx.scene.paint PhongMaterial Color)
             (javafx.scene.shape Box)
             (javafx.scene PerspectiveCamera DepthTest)
             (javafx.scene.transform Rotate Translate)))



(defn- material [color]
  (doto (PhongMaterial. color)
    (.setSpecularColor color)
  ))


(defn- box [w h d]
  (Box. w h d))


(defn- set-3D-axis [parent]
    (let [group
          (fx/group
              (doto (box 100 2 2)
                  (.setMaterial (material fx/RED))
                  (.setTranslateX 50))
              (doto (box 2 100 2)
                  (.setMaterial (material fx/GREEN))
                  (.setTranslateY 50))
              (doto
                  (box 2 2 100)
                  (.setMaterial (material fx/BLUE))
                  (.setTranslateZ 50))
              )
          ]
  (fx/add parent group)))


(defn- set-grid [parent]
    (doseq [i (range -100 100 10)]
        (fx/add parent (fx/line :x1 -200 :x2 200 :y1 i :y2 i :color Color/GRAY))
        (fx/add parent (fx/line :y1 -200 :y2 200 :x1 i :x2 i :color Color/GRAY))
        ))


(defn- build-camera []
    (let [

          t (Translate. 25 -105 -240);(Translate. 50 -150 -250)
          rz (Rotate. 0 Rotate/Z_AXIS)  ;; unused, actually
          ry (Rotate. -4 Rotate/Y_AXIS)  ;; pan
          rx (Rotate. -20 Rotate/X_AXIS) ;; tilt
          transforms-map {:t t :ry ry :rx rx}

          camera
          (doto (PerspectiveCamera. true)
              (.setNearClip 0.1)
              (.setFarClip 1000))

          rotate-group  ;; rotates the camera in all directions, and holds the camera
          (doto (fx/group camera)
              (-> .getTransforms (.setAll [rz ry rx])))

          translate-group  ;; moved the camera around, and holds the rotate-group
          (doto (fx/group rotate-group)
              (-> .getTransforms (.setAll [t])))
          ]

    [camera translate-group transforms-map]))


;; Will hold a reference to the one (and only) screen
(def ^:private screen-singleton (atom nil))


(def ^:private FORWARD_STEP 10)
(def ^:private SLIDE_STEP 5)
(def ^:private ROTATE_STEP 2)


(defn- print-camera-transforms [{:keys [t rx ry]}]
    (println "  pan:" (.getAngle ry) "  tilt:" (.getAngle rx)
             "  x:" (.getX t) "  y:" (.getY t) "  z:" (.getZ t)))


(defn- forward [{:keys [t ry]} step]
    (let [
          pan (.getAngle ry)
          [xf yf] (fxu/degrees->xy-factor pan)
          ]
        ;(println "pan:" pan " xf:" xf " yf:" yf)
        (.setZ t (+ (.getZ t) (* step xf)))
        (.setX t (+ (.getX t) (* step yf)))
        ))


(defn- sideways [{:keys [t ry]} step]
    (let [
          pan (.getAngle ry)
          [xf yf] (fxu/degrees->xy-factor pan)
          ]
        ;(println "slide  pan:" pan " xf:" xf " yf:" yf)
        (.setX t (+ (.getX t) (* step xf)))
        (.setZ t (- (.getZ t) (* step yf)))
        ))


(defn- elevate [camera-transforms step]
    (let [ct camera-transforms]
        (-> ct :t (.setY (+ (-> ct :t .getY) step)))))



(defn- turn [camera-tranforms astep]
    (let [ry (:ry camera-tranforms)]
        (.setAngle ry (+ (.getAngle ry) astep))))

(defn-  tilt [camera-tranforms astep]
    (let [rx (:rx camera-tranforms)]
        (.setAngle rx (+ (.getAngle rx) astep))))



(defn- set-keyhandler [scene camera-transforms]
    (let [
          ct camera-transforms

          keypressedhandler
          (fx/key-pressed-handler
              {
               #{:RIGHT}
               #(turn ct ROTATE_STEP)
               #{:LEFT}
               #(turn ct (- ROTATE_STEP))

               #{:UP}
               #(forward ct FORWARD_STEP)
               #{:DOWN}
               #(forward ct (- FORWARD_STEP))

               #{:CTRL :RIGHT}
               #(sideways ct SLIDE_STEP)
               #{:CTRL :LEFT}
               #(sideways ct (- SLIDE_STEP))

               #{:CTRL :UP}
               #(elevate ct (- SLIDE_STEP))
               #{:CTRL :DOWN}
               #(elevate ct SLIDE_STEP)

               #{:SHIFT :CTRL :UP}
               #(tilt ct ROTATE_STEP)
               #{:SHIFT :CTRL :DOWN}
               #(tilt ct (- ROTATE_STEP))

                #{:C} #(print-camera-transforms ct)
               })
          ]
        (.setOnKeyPressed scene keypressedhandler)))



;; creates and returns a new  visible screen
(defn- create-screen []
  (fx/now
    (let [
          world (fx/group)
          [camera camera-group camera-transforms] (build-camera)

          root (doto (fx/group world camera-group)
                   (.setDepthTest DepthTest/ENABLE))

          scene (fx/scene root :size [400 300] :fill fx/WHITESMOKE :depthbuffer true)
          ;;
          stage
          (fx/now (fx/stage
                    :title "george.turtle"
                    :sizetoscene true
                    :scene scene
                    :onhidden #(reset! screen-singleton nil)))
          ]
      #_(doto root
        (-> .layoutXProperty (.bind (-> stage .widthProperty (.divide 2))))
        (-> .layoutYProperty (.bind (-> stage .heightProperty (.divide 2))))
        )

      (.setCamera scene camera)

      (doto world
          (-> .getTransforms (.setAll
                                 [
                                  (Translate. 0 0 0)
                                  (Rotate. 90 0 0 0 Rotate/X_AXIS)
                                  ]
                                 )))

      (set-3D-axis world)
      (fx/add world (fx/line :x2 50 :y2 50))
      (set-grid world)

      (fx/add world (doto (box 50 50 50)
                        (.setMaterial (material fx/BLUE))
                        (.setTranslateX 75)
                        (.setTranslateY 75)
                        (.setTranslateZ 25)
                        ))

      (set-keyhandler scene camera-transforms)
      (.setUserData stage {:world world :camera-transforms camera-transforms})

      stage)))



;; Creates and show a new screen, or brings the existing screen to front
(defn screen []
  (if @screen-singleton
    @screen-singleton
    (reset! screen-singleton (create-screen))))




(defn -main
  "Launches an input-stage as a stand-alone app."
  [& args]
  (fx/later (screen)))


;;; DEV ;;;

(println "WARNING: Running george.turtle.core/-main" (-main))
