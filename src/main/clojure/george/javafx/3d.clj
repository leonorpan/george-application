;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.javafx.3d
  (:require [george.javafx :as fx])
  (:import (javafx.geometry Point3D)
           (javafx.scene.paint PhongMaterial)
           (javafx.scene.shape Sphere)))




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



(defn- set-material [n color]
  (.setMaterial n (material color))
  n)


(defn- X [p] (if (vector? p) (first p) (.getX p)))
(defn- Y [p] (if (vector? p) (second p) (.getY p)))
(defn- Z [p] (if (vector? p) (get p 2 0) (.getZ p)))



(defn set-translate [n p]
  (doto n
    (.setTranslateX (X p))
    (.setTranslateY (Y p))
    (.setTranslateZ (Z p))))


(defn sphere
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

