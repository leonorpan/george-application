(ns george.util)


(defn- not-keyword? [x]
  (not (keyword? x)))


(defn partition-args
  "returns args in a vector vector: [args kwargs], where args is a seq and kwargs a map.
Optionally a map of default kwargs is applied ot the kwargs"
  ([args] (partition-args args {}))
  ([args default-kwargs]
  [
   (vec (take-while not-keyword? args))
   (merge default-kwargs (apply hash-map (drop-while not-keyword? args)))
   ]))



(defn radians->x-factor [rad]
    (Math/cos rad))

(defn radians->y-factor [rad]
    (Math/sin rad))

(defn degrees->x-factor [deg]
    (radians->x-factor (Math/toRadians deg)))

(defn degrees->y-factor [deg]
    (radians->y-factor (Math/toRadians deg)))

