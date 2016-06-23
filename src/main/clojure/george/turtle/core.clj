(ns
  ^{:author "Terje Dahl"}
  george.turtle.core

  (:require
    [george.javafx.core :as fx]
    :reload)

    (:import (javafx.scene.paint PhongMaterial)
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
        (doto group
            ;(.setTranslateX 150)
            (.setTranslateY 0)
            ;(.setTranslateZ 200)
            )

  (fx/add parent group)))


(defn- set-grid [parent]
    (doseq [i (range -100 100 10)]
        (fx/add parent (fx/line :x1 -200 :x2 200 :y1 i :y2 i))

        ))

(defn- build-camera []
    (let [

          t (Translate. 25 -105 -240);(Translate. 50 -150 -250)
          rz (Rotate. 0 Rotate/Z_AXIS)  ;; unused, actually
          ry (Rotate. -4 Rotate/Y_AXIS)  ;; pan
          rx (Rotate. -20 Rotate/X_AXIS) ;; tilt
          transforms-map {:t t :ry ry :rx rx}

          camera
          (PerspectiveCamera. true)
          rotate-group  ;; rotates the camera in all directions, and holds the camera
          (doto (fx/group camera)
              (-> .getTransforms (.setAll [rz ry rx])))

          translate-group  ;; moved the camera around, and holds the rotate-group
          (doto (fx/group rotate-group)
              (-> .getTransforms (.setAll [t]))
              )

          ]
        (doto camera
        (.setNearClip 0.1)
        (.setFarClip 1000)

        ;(.setRotate 45)

;        (-> .getTransforms (.setAll transforms))
;        (.setRotationAxis Rotate/X_AXIS)
;        (.setRotate 45)

 ;       (.setTranslateX 50)
 ;       (.setTranslateY -50)
 ;       (.setTranslateZ -20)
        )

    [camera translate-group transforms-map]))


;; Will hold a reference to the one (and only) screen
(def ^:private screen-singleton (atom nil))

(def ^:private FORWARD_STEP 10)
(def ^:private SLIDE_STEP 5)
(def ^:private ROTATE_STEP 2)


(defn- print-camera-transforms [ct]
    (let [
          t (:t ct)
          ]
        (println "  pan:" (-> ct :ry .getAngle) "  tilt:" (-> ct :rx .getAngle)
                 "  x:" (.getX t) "  y:" (.getY t) "  z:" (.getZ t))))


(defn- forward [camera-transform step]
    (let [
          ct camera-transform
          t (:t ct)
          pan (-> ct :ry .getAngle)
          _ (println "pan:" pan)

;          a (-> ct :rx .getAngle)
          ]
;        (print-camera-transforms c)
        ;(-> ct :rx (.setAngle 0))
        (-> t (.setZ (+ (-> t .getZ) step)))
        ;(-> ct :rx (.setAngle a))
        ))


(defn- pan [camera-tranforms astep]
    (let [
          ct camera-tranforms
          t (:t ct)
          ry (:ry ct)
          x (.getX t)
          z (.getZ t)
          ]
        ;(print-camera-transforms ct)
        ;(.setX t (+ x x))
        ;(.setY t (+ y y))
        ;(.setPivotX ry x)
        ;(.setPivotZ ry z)
        ;(println "ry:" ry)
        (.setAngle ry (+ (.getAngle ry) astep))
        ;(.setX t x)
        ;(.setY t y)
        ))

#_(defn- pan [camera astep]
    (let [
          c camera
          ]
        (doto c
            (.setRotationAxis Rotate/Y_AXIS)
            (.setRotate (+ (-> c .getRotate) astep)))))


#_(comment
      #{:UP}
      #(-> ct :t (.setZ (+ (-> ct :t .getZ) FORWARD_STEP)))
      #{:DOWN}
      #(-> ct :t (.setZ (- (-> ct :t .getZ) FORWARD_STEP)))
      )

(defn-  tilt [camera-tranforms astep]
    (let [
          ct camera-tranforms
          t (:t ct)
          x (.getX t)
          y (.getY t)
          ]
        (print-camera-transforms ct)
        ;(.setX t (+ x x))
        ;(.setY t (+ y y))
        (-> ct :rx (.setAngle (+ (-> ct :rx .getAngle) astep)))
        ;(.setX t x)
        ;(.setY t y)
        ))


(defn- slide [camera-transforms step]
    (let [ct camera-transforms]
        (-> ct :t (.setX (+ (-> ct :t .getX) step)))))


(defn- elevate [camera-transforms step]
    (let [ct camera-transforms]
        (-> ct :t (.setY (+ (-> ct :t .getY) step)))))


(defn- set-keyhandler [scene camera camera-transforms]
    (let [
          c camera
          ct camera-transforms

          keypressedhandler
          (fx/key-pressed-handler
              {
               #{:RIGHT}
               #(pan ct ROTATE_STEP)
               #{:LEFT}
               #(pan ct (- ROTATE_STEP))

               #{:UP}
               #(forward ct FORWARD_STEP)
               #{:DOWN}
               #(forward ct (- FORWARD_STEP))

               #{:CTRL :RIGHT}
               #(slide ct SLIDE_STEP)
               #{:CTRL :LEFT}
               #(slide ct (- SLIDE_STEP))

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
        (.setOnKeyPressed scene keypressedhandler)
        ))



;; creates and returns a new  visible screen
(defn- create-screen []
  (fx/now
    (let [
          world (fx/group)
          [camera camera-group camera-transforms] (build-camera)

          root (doto (fx/group world camera-group)
                   (.setDepthTest DepthTest/ENABLE))

          scene (fx/scene root :size [400 300] :fill fx/WHITESMOKE :depthbuffer true)
          ;; SceneAntialiasing.BALANCED
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

      (set-keyhandler scene camera camera-transforms)


      (.setUserData stage {:world world :camera camera})

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
