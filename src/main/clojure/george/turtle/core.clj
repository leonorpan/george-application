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
             (javafx.scene.shape Box Cylinder Sphere)
             (javafx.scene PerspectiveCamera DepthTest)
             (javafx.scene.transform Rotate Translate)
             (javafx.geometry Point3D)))




(defn- ensure-Point3D [p]
    (cond
        (vector? p)
        (condp = (count p)
            2
            (Point3D. (first p) (second p) 0)
            3
            (Point3D. (first p) (second p) (get p 2))
            ;; default
            (throw (IllegalArgumentException. "point must be vector of [x y] or [x y z]")))

        (instance? Point3D p)
        p

        :default
        (throw (IllegalArgumentException. (str "Unknown type for point:" p "  Must be [x y z] or Point3D")))))



(defn- material [color]
  (doto (PhongMaterial. color)
    (.setSpecularColor (.darker color))
  ))


(defn- box [w h d]
  (Box. w h d))



(defn- cylinder [radius height]
    (Cylinder. radius height))


(defn- X [p] (if (vector? p) (first p) (.getX p)))
(defn- Y [p] (if (vector? p) (second p) (.getY p)))
(defn- Z [p] (if (vector? p) (get p 2 0) (.getZ p)))


(defn- set-translate [n p]
    (doto n
        (.setTranslateX (X p))
        (.setTranslateY (Y p))
        (.setTranslateZ (Z p))))


(defn- set-material [n color]
    (.setMaterial n (material color))
    n)



(defn- sphere
    ([]
     (sphere [0 0 0]))
    ([loc-p]
     (sphere loc-p 0.5))
    ([loc-p radius]
     (sphere loc-p radius fx/ANTHRECITE))
     ([loc-p radius color]
      (let [loc-p (ensure-Point3D loc-p)]
          (doto (Sphere. radius)
              (set-material color)
              (set-translate loc-p))))
    )



(defn- bar
    ([start-p end-p]
     (bar start-p end-p 0.5))
    ([start-p end-p radius]
     (bar start-p end-p radius fx/ANTHRECITE))
    ([start-p end-p radius color]
     (let [
           start-p (ensure-Point3D start-p)
           end-p (ensure-Point3D end-p)
           res-vec (.subtract start-p end-p)
           length (.magnitude res-vec)
           mid (.midpoint start-p end-p)
           y-axis-angle (.angle Rotate/Y_AXIS res-vec)
           rotation-axis (.crossProduct Rotate/Y_AXIS res-vec)
           ]
         (doto (cylinder radius length)
             (.setMaterial (material color))
             (-> .getTransforms
                 (.setAll [(Translate. (.getX mid) (.getY mid) (.getZ mid))
                           (Rotate. y-axis-angle rotation-axis)])))))
    )





(defn- axis-3D []
    ;; http://netzwerg.ch/blog/2015/03/22/javafx-3d-line/
    (let [
          group
          (fx/group
              (doto (cylinder 1 100)
                  (.setMaterial (material fx/RED))
                  (.setRotationAxis Rotate/Z_AXIS)
                  (.setRotate 90)
                  (.setTranslateX 50))

              (doto (cylinder 1 100)
                  (.setMaterial (material fx/GREEN))
                  (.setTranslateY 50))

              (doto (cylinder 1 100)
                  (.setMaterial (material fx/BLUE))
                  (.setRotationAxis Rotate/X_AXIS)
                  (.setRotate 90)
                  (.setTranslateZ 50))

              (doto (sphere [0 0] 2)
                  (.setMaterial (material Color/GRAY)))

              #_(comment
                  (bar [100 0 0] [0 100 0] 1 Color/ORANGE)
                  (bar [0 100 0] [0 0 100] 1 Color/PURPLE)
                  (bar [0 0 100] [100 0 0] 1 Color/BROWN)
                  (bar [100 0 0] [100 100 100] 1 Color/ORANGE)
                  (bar [0 100 0] [100 100 100] 1 Color/PURPLE)
                  (bar [0 0 100] [100 100 100] 1 Color/BROWN)
                  )
              )
          ]
  group))



(defn- grid-2D []
    (let [
          ;w 400
          ;h 300
          ;bleed 50
          ]
        (apply fx/group
               (concat
                   ;; horizontal lines
                   (for [i (range -150 160 10)]
                       (fx/line :x1 -200 :x2 200 :y1 i :y2 i :color Color/GRAY))
                   ;; vertical lines
                   (for [i (range -200 210 10)]
                       (fx/line :y1 -150 :y2 150 :x1 i :x2 i :color Color/GRAY))))
        ))


(defn- create-turtle []

    (doto
        (fx/group
            (doto
                (fx/group
                    (sphere [0 0] 1)
                    (bar [0 0] [2.5 -5] 1) ;; right front
                    (sphere [2.5 -5] 1)
                    (bar [2.5 -5] [0 -4] 1) ;; right back
                    (sphere [0 -4] 1)
                    (bar [0 -4] [-2.5 -5] 1) ;; left back
                    (sphere [-2.5 -5] 1)
                    (bar [-2.5 -5] [0 0] 1) ;; left front
                    )
                (set-translate [0 2.5])  ;; center turtle (compensate for drawing from [0 0])
                (.setRotate -90)  ;; face right (along x-axis)
                )
            )
        (.setRotate 90)  ;; start turtle facing up (along y-axis): 90 degrees.
        ))


(defn- build-camera []
    (let [

          camera
          (doto (PerspectiveCamera. true)
              (.setNearClip 0.1)
              (.setFarClip 1000))

          t (Translate. 25 -105 -240);(Translate. 50 -150 -250)
          ry (Rotate. -4 Rotate/Y_AXIS)  ;; pan
          rx (Rotate. -20 Rotate/X_AXIS) ;; tilt
          transforms-map {:t t :ry ry :rx rx :camera camera}


          rotate-group  ;; rotates the camera in all directions, and holds the camera
          (doto (fx/group camera)
              (-> .getTransforms (.setAll [ry rx])))

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


(defn- c-forward [{:keys [t ry]} step]
    (let [
          pan (.getAngle ry)
          [xf yf] (fxu/degrees->xy-factor pan)
          ]
        ;(println "pan:" pan " xf:" xf " yf:" yf)
        (.setZ t (+ (.getZ t) (* step xf)))
        (.setX t (+ (.getX t) (* step yf)))
        ))


(defn- c-sideways [{:keys [t ry]} step]
    (let [
          pan (.getAngle ry)
          [xf yf] (fxu/degrees->xy-factor pan)
          ]
        ;(println "slide  pan:" pan " xf:" xf " yf:" yf)
        (.setX t (+ (.getX t) (* step xf)))
        (.setZ t (- (.getZ t) (* step yf)))
        ))


(defn- c-elevate [camera-transforms step]
    (let [ct camera-transforms]
        (-> ct :t (.setY (+ (-> ct :t .getY) step)))))



(defn- c-turn [camera-tranforms astep]
    (let [ry (:ry camera-tranforms)]
        (.setAngle ry (+ (.getAngle ry) astep))))


(defn-  c-tilt [camera-tranforms astep]
    (let [rx (:rx camera-tranforms)]
        (.setAngle rx (+ (.getAngle rx) astep))))




(defn- c-transition [to {:keys [t ry rx camera] :as camera-transformations}]
    (let [
          ]
    (condp = to
        :2D
        (.play (fx/simple-timeline 500 nil
                                    [(.angleProperty ry) 0]
                                    [(.angleProperty rx) -90]
                                    [(.xProperty t) 0]
                                    [(.yProperty t) -570]
                                    [(.zProperty t) 0]
                                    ))

        :3D
        (.play (fx/simple-timeline 500 nil
                                   [(.angleProperty ry) -4]
                                    [(.angleProperty rx) -20]
                                    [(.xProperty t) 25]
                                    [(.yProperty t) -105]
                                    [(.zProperty t) -270]
                                    ))


        (throw (IllegalArgumentException. (str "Unknown arg: 'to': " to))))))



(defn- set-keyhandler [scene camera-transforms]
    (let [
          ct camera-transforms

          keypressedhandler
          (fx/key-pressed-handler
              {
               #{:RIGHT}
               #(c-turn ct ROTATE_STEP)
               #{:LEFT}
               #(c-turn ct (- ROTATE_STEP))

               #{:UP}
               #(c-forward ct FORWARD_STEP)
               #{:DOWN}
               #(c-forward ct (- FORWARD_STEP))

               #{:CTRL :RIGHT}
               #(c-sideways ct SLIDE_STEP)
               #{:CTRL :LEFT}
               #(c-sideways ct (- SLIDE_STEP))

               #{:CTRL :UP}
               #(c-elevate ct (- SLIDE_STEP))
               #{:CTRL :DOWN}
               #(c-elevate ct SLIDE_STEP)

               #{:SHIFT :CTRL :UP}
               #(c-tilt ct ROTATE_STEP)
               #{:SHIFT :CTRL :DOWN}
               #(c-tilt ct (- ROTATE_STEP))

                #{:C} #(print-camera-transforms ct)

               #{:CTRL :DIGIT2} #(c-transition :2D camera-transforms)
               #{:CTRL :DIGIT3} #(c-transition :3D camera-transforms)
               })
          ]
        (.setOnKeyPressed scene keypressedhandler)))


(def ^:private current-turtle-atom (atom nil))

;; creates and returns a new  visible screen
(defn- create-screen []
  (fx/now
    (let [
          world (fx/group)
          [camera camera-group camera-transforms] (build-camera)
          root (doto (fx/group world camera-group) (.setDepthTest DepthTest/ENABLE))
          scene (fx/scene root :size [600 600] :fill fx/WHITESMOKE :depthbuffer true)
          stage
          (fx/now (fx/stage
                    :title "Turtle - graphics"
                    :sizetoscene true
                    :scene scene
                    :onhidden #(reset! screen-singleton nil)))
          ]

      (.setCamera scene camera)

      (doto world
          (-> .getTransforms (.setAll
                                 [
                                  (Translate. 0 0 0)
                                  (Rotate. 90 0 0 0 Rotate/X_AXIS)
                                  ]
                                 )))

      (fx/add world (axis-3D))
      (fx/add world (grid-2D))

      (fx/add world (doto (reset! current-turtle-atom (create-turtle))
                        (.setTranslateX 20)
                        (.setTranslateY -20)
                        ))

      (set-keyhandler scene camera-transforms)
      (.setUserData stage {:world world :camera-transforms camera-transforms})

      stage)))





(defn- current-turtle []
    (let [

          ]

        @current-turtle-atom))


;;;; API ;;;;


;; Creates and show a new screen, or brings the existing screen to front
(defn screen []
    (if @screen-singleton
        @screen-singleton
        (reset! screen-singleton (create-screen))))

(defn angle [turtle]
    (.getRotate turtle))


(defn x [turtle]
    (.getLayoutX turtle))


(defn y [turtle]
    (.getLayoutY turtle))

(defn forward
    ([distance]
     (forward (current-turtle) distance))
    ([turtle distance]
     (let [ang (angle turtle)
           x (x turtle)
           y (y turtle)
           line nil ;(if (:down @pen) (fx/line :x1 x :y1 y :color (Color/web (:color @pen))))
           [x-factor y-factor] (fxu/degrees->xy-factor ang)
           new-x (+ x (* distance x-factor))
           new-y (+ y (* distance y-factor))
           ]
         #_(when line
               (add-node (:screen @pen) line)
               (fx/later (. node toFront)))

         (fx/synced-keyframe
             (* (/ (Math/abs distance) 600) 1000)  ;; 600 px per second
             [(.layoutXProperty turtle) new-x]
             [(.layoutYProperty turtle) new-y]
             (if line [(.endXProperty line) new-x])
             (if line [(.endYProperty line) new-y])
             )
         turtle))
    )


(defn left
        ([degrees]
         (left (current-turtle) degrees))
    ([turtle degrees]
     (let [new-angle (+ (angle turtle) degrees)]
         (fx/synced-keyframe
             (* (/ (Math/abs degrees) (* 3 360)) 1000)  ;; 3 rotations pr second
             [(.rotateProperty turtle) new-angle]
             )
         turtle))
    )


(defn right
    ([degrees]
     (right (current-turtle) degrees))
    ([turtle degrees]
     (left turtle (- degrees)))
    )



;;;; main ;;;;

(defn -main
  "Launches an input-stage as a stand-alone app."
  [& args]
  (fx/later (screen)))


;;; DEV ;;;

(println "WARNING: Running george.turtle.core/-main" (-main))
