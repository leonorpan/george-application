(ns
  ^{:author "Terje Dahl"}
  george.application.turtle.turtle3D

  (:require
      [george.javafx :as fx]
      [george.javafx.java :as fxj]
      [george.javafx.util :as fxu])


  (:import (javafx.scene.paint PhongMaterial Color)
           (javafx.scene.shape Box Cylinder Sphere)
           (javafx.scene PerspectiveCamera DepthTest Node)
           (javafx.scene.transform Rotate Translate)
           (javafx.geometry Point3D)))



(defn- assoc-userdata [^Node n k v]
    (.setUserData n
                  (assoc (if-let [m (.getUserData n)] m {})
                      k v))
    n)



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
    (.setSpecularColor (.darker color))))



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
             (set-translate loc-p)))))




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
           rotation-axis (.crossProduct Rotate/Y_AXIS res-vec)]


         (doto (cylinder radius length)
             (.setMaterial (material color))
             (-> .getTransforms
                 (.setAll [(Translate. (.getX mid) (.getY mid) (.getZ mid))
                           (Rotate. y-axis-angle rotation-axis)]))))))





(defn- create-origo []
    (sphere [0 0] 2 Color/GRAY))


(defn- create-axis-3D []
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


              #_(comment
                  (bar [100 0 0] [0 100 0] 1 Color/ORANGE)
                  (bar [0 100 0] [0 0 100] 1 Color/PURPLE)
                  (bar [0 0 100] [100 0 0] 1 Color/BROWN)
                  (bar [100 0 0] [100 100 100] 1 Color/ORANGE)
                  (bar [0 100 0] [100 100 100] 1 Color/PURPLE)
                  (bar [0 0 100] [100 100 100] 1 Color/BROWN)))]



     group))



(defn- create-grid-2D []
    (let []
          ;w 400
          ;h 300
          ;bleed 50

        (apply fx/group
               (concat
                   ;; horizontal lines
                   (for [i (range -150 160 10)]
                       (fx/line :x1 -200 :x2 200 :y1 i :y2 i :color Color/LIGHTGRAY))
                   ;; vertical lines
                   (for [i (range -200 210 10)]
                       (fx/line :y1 -150 :y2 150 :x1 i :x2 i :color Color/LIGHTGRAY))))))



(defn- create-turtle []
    (let [r 1.25]
     (doto
         (fx/group
             (doto
                 (fx/group
                     (sphere [0 0] r)
                     (bar [0 0] [2.5 -5] r) ;; right front
                     (sphere [2.5 -5] r)
                     (bar [2.5 -5] [0 -4] r) ;; right back
                     (sphere [0 -4] r)
                     (bar [0 -4] [-2.5 -5] r) ;; left back
                     (sphere [-2.5 -5] r)
                     (bar [-2.5 -5] [0 0] r)) ;; left front

                 (set-translate [0 2.5])  ;; center turtle (compensate for drawing from [0 0])
                 (.setRotate -90)))  ;; face right (along x-axis)


         (.setRotate 90)  ;; start turtle facing up (along y-axis): 90 degrees.
         (.setUserData {:color "BLACK" :pendown true}))))



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
          [xf yf] (fxu/degrees->xy-factor pan)]

        ;(println "pan:" pan " xf:" xf " yf:" yf)
        (.setZ t (+ (.getZ t) (* step xf)))
        (.setX t (+ (.getX t) (* step yf)))))



(defn- c-sideways [{:keys [t ry]} step]
    (let [
          pan (.getAngle ry)
          [xf yf] (fxu/degrees->xy-factor pan)]

        ;(println "slide  pan:" pan " xf:" xf " yf:" yf)
        (.setX t (+ (.getX t) (* step xf)))
        (.setZ t (- (.getZ t) (* step yf)))))



(defn- c-elevate [camera-transforms step]
    (let [ct camera-transforms]
        (-> ct :t (.setY (+ (-> ct :t .getY) step)))))



(defn- c-turn [camera-tranforms astep]
    (let [ry (:ry camera-tranforms)]
        (.setAngle ry (+ (.getAngle ry) astep))))


(defn-  c-tilt [camera-tranforms astep]
    (let [rx (:rx camera-tranforms)]
        (.setAngle rx (+ (.getAngle rx) astep))))




(defn- c-transition [to {:keys [origo axis grid] {:keys [t ry rx]} :camera}]
    (let []

        (condp = to
            :2D
            (.play (fx/simple-timeline 500
                                       #(doseq [n [origo axis grid]] (.setVisible n false))
                                       [(.angleProperty ry) 0]
                                       [(.angleProperty rx) -90]
                                       [(.xProperty t) 0]
                                       [(.yProperty t) -570]
                                       [(.zProperty t) 0]))


            :3D
            (do
                (doseq [n [origo axis grid]] (.setVisible n true))
                (.play (fx/simple-timeline 500
                                           nil
                                           [(.angleProperty ry) -4]
                                           [(.angleProperty rx) -20]
                                           [(.xProperty t) 25]
                                           [(.yProperty t) -105]
                                           [(.zProperty t) -270])))



            (throw (IllegalArgumentException. (str "Unknown arg: 'to': " to))))))



(defn set-keyhandler [scene {:keys [origo axis grid camera] :as state}]
    (let [

          keypressedhandler
          (fx/key-pressed-handler
              {
               #{:RIGHT}
                #(c-turn camera ROTATE_STEP)
               #{:LEFT}
                #(c-turn camera (- ROTATE_STEP))

               #{:UP}
                #(c-forward camera FORWARD_STEP)
               #{:DOWN}
                #(c-forward camera (- FORWARD_STEP))

               #{:CTRL :RIGHT}
                #(c-sideways camera SLIDE_STEP)
               #{:CTRL :LEFT}
                #(c-sideways camera (- SLIDE_STEP))

               #{:CTRL :UP}
                #(c-elevate camera (- SLIDE_STEP))
               #{:CTRL :DOWN}
                #(c-elevate camera SLIDE_STEP)

               #{:SHIFT :CTRL :UP}
                #(c-tilt camera ROTATE_STEP)
               #{:SHIFT :CTRL :DOWN}
                #(c-tilt camera (- ROTATE_STEP))

               #{:CTRL :DIGIT2} #(c-transition :2D state)
               #{:CTRL :DIGIT3} #(c-transition :3D state)

               #{:CTRL :C}      #(print-camera-transforms camera)
               #{:CTRL :O}      #(.setVisible origo (not (.isVisible origo)))
               #{:CTRL :A}      #(.setVisible axis (not (.isVisible axis)))
               #{:CTRL :G}      #(.setVisible grid (not (.isVisible grid)))})]



        (.setOnKeyPressed scene keypressedhandler)))



(def ^:private current-turtle-atom (atom nil))


(declare screen)


(defn- current-turtle []
    (if-not @current-turtle-atom
        (do (screen) (recur))
        @current-turtle-atom))



(defn- get-userdata [t k]
    (-> t .getUserData k))



;;;; API ;;;;


(defn angle [turtle]
    (.getRotate turtle))


(defn x [turtle]
    (.getTranslateX turtle))


(defn y [turtle]
    (.getTranslateY turtle))

(defn- z [turtle]
    (.getTranslateY turtle))

(defn position
    ([x y]
     (position (current-turtle) x y))
    ([turtle x y]
     (let []

         (fx/synced-keyframe
             250  ;; 600 px per second
             [(.translateXProperty turtle) x]
             [(.translateYProperty turtle) y]))
             ;         (if line [(.endXProperty line) new-x])
             ;        (if line [(.endYProperty line) new-y])


     turtle))



(defn forward
    ([distance]
     (forward (current-turtle) distance))
    ([turtle distance]
     (let [ang (angle turtle)
           x (x turtle)
           y (y turtle)
           z (z turtle)
           [x-factor y-factor] (fxu/degrees->xy-factor ang)
           new-x (+ x (* distance x-factor))
           new-y (+ y (* distance y-factor))

           line
           (when (get-userdata turtle :pendown)
               (doto  (fx/line
                          :x1 x
                          :y1 y
                          :color (Color/web (get-userdata turtle :color)))
                   #_(bar [x y z][new-x new-y z] 0.5 (Color/web (get-userdata turtle :color)))
                   (assoc-userdata :clearable true)))]


         (when line
             (fx/now
                 (fx/add (get-userdata turtle :world) line)
                (. turtle toFront)))

         (fx/synced-keyframe
             (* (/ (Math/abs distance) 600) 1000)  ;; 600 px per second
             [(.translateXProperty turtle) new-x]
             [(.translateYProperty turtle) new-y]
             (if line [(.endXProperty line) new-x])
             (if line [(.endYProperty line) new-y]))

         turtle)))



(defn heading
    ([degrees]
     (heading (current-turtle) degrees))
    ([turtle degrees]
     (fx/synced-keyframe
         200
         [(.rotateProperty turtle) degrees])

     turtle))


(defn left
    ([degrees]
     (left (current-turtle) degrees))
    ([turtle degrees]
     (let [new-angle (+ (angle turtle) degrees)]
         (fx/synced-keyframe
             (* (/ (Math/abs degrees) (* 3 360)) 1000)  ;; 3 rotations pr second
             [(.rotateProperty turtle) new-angle])

         turtle)))



(defn right
    ([degrees]
     (right (current-turtle) degrees))
    ([turtle degrees]
     (left turtle (- degrees))))




(defn color
    "returns the color as a 'web-compatible' color name."
    ([]
     (color (current-turtle)))
    ([t]
     (get-userdata t :color)))




(defn set-color
    "set the color as a 'web-compatible' color name."
    ([c]
     (set-color (current-turtle) c))
    ([t c]
     (.setUserData t (assoc (.getUserData t) :color c))
     t))


(defn pen-down
    ([]
     (pen-down (current-turtle)))
    ([t]
     (assoc-userdata t :pendown true)))


(defn pen-up
    ([]
     (pen-up (current-turtle)))
    ([t]
     (assoc-userdata t :pendown false)))


(defn clear
    ([]
     (clear (current-turtle)))
    ([t]
     (let [
           world (get-userdata t :world)
           filtered (filter #(not (get-userdata % :clearable)) (.getChildren world))]

         (fx/later (-> world .getChildren (.setAll filtered)))
         t)))

(defn hide
    ([]
     (hide (current-turtle)))
    ([t]
     (.setVisible t false)
     t))


(defn show
    ([]
     (show (current-turtle)))
    ([t]
     (.setVisible t true)
     t))


(defn home
    ([]
     (home (current-turtle)))
    ([turtle]
     (show turtle)
     (heading turtle 90)
     (position turtle 0 0)))


(defn reset
    ([]
     (reset (current-turtle)))
    ([t]
     (println "t:" t)
     (clear t)
     (show t)
     (home t)
     (assoc-userdata t :color "BLACK")
     (assoc-userdata t :pendown true)
     t))





(defn- colored-square []
    (doseq [c ["BLACK" "RED" "BLUE" "GREEN"]]
        (set-color c)
        (forward 30)
        (left 90)))


(declare dev-run)

;; creates and returns a new  visible screen
(defn- create-screen []
  (fx/now
    (let [
          world (fx/group)
          {:keys [c n] :as camera} (build-camera)

          turtle
          (doto (reset! current-turtle-atom (create-turtle))
              (assoc-userdata :world world))

          root (doto (fx/group world n) (.setDepthTest DepthTest/ENABLE))
          scene (fx/scene root :size [600 600] :fill fx/WHITESMOKE :depthbuffer true)
          stage
          (fx/now (fx/stage
                    :title "Turtle Geometry - screen"
                    :sizetoscene true
                    :location [300 110]
                    :scene scene
                    :onhidden #(reset! screen-singleton nil)))

          origo (create-origo)
          axis (create-axis-3D)
          grid (create-grid-2D)
          state {:camera camera :origo origo :axis axis :grid grid}]


      (.setCamera scene c)

      (set-keyhandler scene state)

      (doto world
          (-> .getTransforms (.setAll
                                 [
                                  ;(Translate. 0 0 0)
                                  (Rotate. 90 0 0 0 Rotate/X_AXIS)])))




      (fx/add world origo)
      (fx/add world axis)
      (.setVisible axis false)
      (fx/add world grid)
      (fx/add world turtle)


      ;; a small startup animation
      (heading 180)
      (position 100 -50)
      (fxj/thread
          (Thread/sleep 700)
          (position 0 -50)
          (colored-square)
          (Thread/sleep 700)
          (c-transition :2D state)
          (clear)
          (Thread/sleep 700)
          (colored-square)
          (Thread/sleep 700)
          (clear)
          (heading 90)
          (home)
          (reset)
          (dev-run))


      (.setUserData stage {})

      stage)))








;; Creates and show a new screen, or brings the existing screen to front
(defn screen []
    (if @screen-singleton
        (doto @screen-singleton
            (.toFront))
        (reset! screen-singleton (create-screen))))




;;;; main ;;;;

(defn -main
  "Launches an input-stage as a stand-alone application."
  [& args]
  (fx/later (screen)))


;;; DEV ;;;

;(println "WARNING: Running george.turtle.core/-main" (-main))



(defn dev-run []
    (println "dev-run called ..."))
