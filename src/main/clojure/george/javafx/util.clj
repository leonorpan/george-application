;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.javafx.util)


(defn- not-keyword? [x]
  (not (keyword? x)))


(defn partition-args
  "returns args in a vector vector: [args kwargs], where args is a seq and kwargs a map.
Optionally a map of default kwargs is applied to the kwargs.
If default-kwargs is supplies, then keywords not present in default will throw IllegalArgumentException."

    ([all-args]
     (partition-args all-args nil))

  ([all-args default-kwargs]
   (let [args (vec (take-while not-keyword? all-args))
         kwargs (apply hash-map (drop-while not-keyword? all-args))]

       (if-not default-kwargs
           ;; return args and kwargs as-is
           [args kwargs]
           ;; check that all keyword are in default
           (let [unknowns (filter #(not ((set (keys default-kwargs)) %)) (keys kwargs))]
               (if (not-empty unknowns)
                   (throw (IllegalArgumentException. (str "Unknown keywords: " (seq unknowns))))
                   [args (merge default-kwargs kwargs)]))))))



(defn radians->x-factor [rad]
    (Math/cos rad))

(defn radians->y-factor [rad]
    (Math/sin rad))

(defn degrees->x-factor [deg]
    (radians->x-factor (Math/toRadians deg)))

(defn degrees->y-factor [deg]
    (radians->y-factor (Math/toRadians deg)))

(defn degrees->xy-factor [deg]
    [(degrees->x-factor deg) (degrees->y-factor deg)])

(defn hypotenuse [x y]
  (Math/sqrt (+ (* x x) (* y y))))