(ns
  ^{:author "Terje Dahl" }
  george.util)

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