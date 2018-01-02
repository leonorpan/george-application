;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  george.example.moleculesampleapp

  (:require
    [george.javafx :as fx]
    [george.javafx.java :as fxj])



  (:import (javafx.scene DepthTest Scene PerspectiveCamera Node)
           (javafx.scene.paint Color PhongMaterial)
           (moleculesampleapp Xform)
           (javafx.scene.shape Box Cylinder Sphere)
           (javafx.scene.transform Rotate)
           (javafx.geometry Point2D)))

(def CAMERA_INITIAL_DISTANCE -450)
(def CAMERA_INITIAL_X_ANGLE 70.0)
(def CAMERA_INITIAL_Y_ANGLE 320.0)
(def CAMERA_NEAR_CLIP 0.1)
(def CAMERA_FAR_CLIP 10000.0)
(def AXIS_LENGTH 250.0)
(def HYDROGEN_ANGLE 104.5)
(def CONTROL_MULTIPLIER 0.1)
(def SHIFT_MULTIPLIER 10.0)
(def MOUSE_SPEED 0.1)
(def ROTATION_SPEED 2.0)
(def TRACK_SPEED 0.3)

(def axisGroup (Xform.))
(def moleculeGroup (Xform.))
(def camera (PerspectiveCamera. true))
(def cameraXform (Xform.))
(def cameraXform2 (Xform.))
(def cameraXform3 (Xform.))



(defn- build-material [c1 c2]
    (doto (PhongMaterial. c1)
        (.setSpecularColor c2)))


(defn- white-material []
    (build-material Color/WHITE Color/LIGHTBLUE))

(defn- gray-material []
    (build-material Color/GRAY Color/DARKGRAY))

(defn- red-material []
    (build-material Color/RED Color/DARKRED))

(defn- blue-material []
    (build-material Color/BLUE Color/DARKBLUE))

(defn- green-material []
    (build-material Color/GREEN Color/DARKGREEN))


(defn- build-camera []
    (fx/add cameraXform cameraXform2)
    (fx/add cameraXform2 cameraXform3)
    (fx/add cameraXform3 camera)
    (.setRotateZ cameraXform3 180.)
    (doto camera
        (.setNearClip CAMERA_NEAR_CLIP)
        (.setFarClip CAMERA_FAR_CLIP)
        (.setTranslateZ CAMERA_INITIAL_DISTANCE))
    (doto cameraXform
        (-> .rx (.setAngle CAMERA_INITIAL_X_ANGLE))
        (-> .ry (.setAngle CAMERA_INITIAL_Y_ANGLE)))

  cameraXform)


(defn- build-axes []
    (doseq [[m w h d]
            [
             [(red-material) AXIS_LENGTH 1 1]
             [(green-material) 1 AXIS_LENGTH 1]
             [(blue-material) 1 1 AXIS_LENGTH]]]

        (fx/add axisGroup (doto (Box. w h d) (.setMaterial m))))
    (.setVisible axisGroup false)
    axisGroup)


(defn- build-bond []
    (doto (Cylinder. 5 100)
        (.setMaterial (gray-material))))


(defn- hydrogen-sphere []
    (doto (Sphere. 30.)
        (.setMaterial (white-material))))


(defn- oxygen-sphere []
    (doto (Sphere. 40.)
        (.setMaterial (red-material))))


(defn- build-molecule []
    (let [
          o (oxygen-sphere)
          h1 (doto (hydrogen-sphere) (.setTranslateX 0.))
          h2 (doto (hydrogen-sphere) (.setTranslateZ 0.))

          b1 (doto (build-bond)
                 (.setTranslateX 50.)
                 (.setRotationAxis Rotate/Z_AXIS)
                 (.setRotate 90.))

          b2 (doto (build-bond)
                 (.setTranslateX 50.)
                 (.setRotationAxis Rotate/Z_AXIS)
                 (.setRotate 90.))

          o-f (Xform. [o])
          h1-f (doto (Xform. [h1]) (.setTx 100.))
          h2-f (doto (Xform. [h2]) (.setTx 100.))
          h1-side-f (Xform. [h1-f b1])
          h2-side-f (doto (Xform. [h2-f b2]) (.setRotateY HYDROGEN_ANGLE))
          m-f (Xform. [o-f h1-side-f h2-side-f])]


        (doto moleculeGroup
            (fx/add m-f))))



(defn- handle-keyboard [scene]
    (.setOnKeyPressed scene
                      (fx/key-pressed-handler
                          {#{:Z} #(do
                                     (.setTranslateZ camera CAMERA_INITIAL_DISTANCE)
                                     (doto cameraXform
                                         (-> .rx (.setAngle CAMERA_INITIAL_X_ANGLE))
                                         (-> .ry (.setAngle CAMERA_INITIAL_Y_ANGLE)))
                                     (doto cameraXform2
                                         (-> .t (.setX 0))
                                         (-> .t (.setY 0))))
                           #{:X} #(.setVisible axisGroup (not (.isVisible axisGroup)))
                           #{:V} #(.setVisible moleculeGroup (not (.isVisible moleculeGroup)))})))


(definterface IPoint
    (x [])
    (y [])
    (set [x y])
    (copy [point2]))


(deftype Point [^:volatile-mutable _x ^:volatile-mutable _y]
    IPoint
    (x [_] _x)
    (y [_] _y)
    (set [_ x y] (set! _x x) (set! _y y))
    (copy [this point2] (.set this (.x point2) (.y point2))))


(defn- handle-mouse [scene]
    (let [
          curr-pos (Point. 0 0)
          prev-pos (Point. 0 0)]

        (.setOnMousePressed
            scene
            (fx/event-handler-2
                [_ me]
                (.set curr-pos (.getSceneX me) (.getSceneY me))))
                ;(.copy prev-pos curr-pos)


        (.setOnMouseDragged
            scene
            (fx/event-handler-2
                [_ me]
                (.copy prev-pos curr-pos)
                (.set curr-pos (.getSceneX me) (.getSceneY me))
                (let [
                      deltaX (- (.x curr-pos) (.x prev-pos))
                      deltaY (- (.y curr-pos) (.y prev-pos))
                      modifier (cond
                                   (.isControlDown me) CONTROL_MULTIPLIER
                                   (.isShiftDown me) SHIFT_MULTIPLIER
                                   :default 1.)]


                    (cond
                        (.isPrimaryButtonDown me)
                        (doto cameraXform
                            (-> .rx (.setAngle (- (-> cameraXform .rx .getAngle) (* deltaY MOUSE_SPEED modifier ROTATION_SPEED))))
                            (-> .ry (.setAngle (+ (-> cameraXform .ry .getAngle) (* deltaX MOUSE_SPEED modifier ROTATION_SPEED)))))

                        (.isSecondaryButtonDown me)
                        (.setTranslateZ camera (+ (.getTranslateZ camera) (* deltaX MOUSE_SPEED modifier)))

                        (.isMiddleButtonDown me)
                        (doto (.t cameraXform2)
                            (.setX (+ (.. cameraXform2 t getX) (* deltaX MOUSE_SPEED modifier TRACK_SPEED)))
                            (.setY (+ (.. cameraXform2 t getY) (* deltaY MOUSE_SPEED modifier TRACK_SPEED))))))))))





(defn- start[]
  (fx/now
    (let [
          world (Xform.)

          root (doto (fx/group world)
                 (.setDepthTest DepthTest/ENABLE))

          _ (fx/add root (build-camera))
          _ (fx/add world (build-axes))
          _ (fx/add world (build-molecule))

          scene (doto (fx/scene root
                                :size [800 600]
                                :depthbuffer true
                                :fill Color/GRAY)
                    (.setCamera camera)
                    (handle-keyboard)
                    (handle-mouse))


          stage (fx/stage
                  :title "Molecule Sample App"
                  :scene scene
                  :sizetoscene true)]



      stage)))


(defn -main
  "Launches an input-stage as a stand-alone application."
  [& args]
  (fx/later (start)))


;;; DEV ;;;

;(println "WARNING: Running george.sandbox.moleculesampleapp/-main" (-main))