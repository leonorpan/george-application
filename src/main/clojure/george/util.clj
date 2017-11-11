;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.util
  (:require [clojure.pprint :as cpp])
  (:import (java.util UUID)
           (java.io File)
           (clojure.lang PersistentVector)))


(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(defn pprint-str
  "returns a pprint-formatted str"
  [data]
  (cpp/write data :stream nil))
  ;; is this better or worse than (with-out-str (cpp data))?


(defn uuid
  "Returns a new UUID string."
  []
  (str (UUID/randomUUID)))


;; from Versions.java in george-client
(def IS_MAC  (-> (System/getProperty "os.name") .toLowerCase (.contains "mac")))

(def IS_WINDOWS (-> (System/getProperty "os.name") .toLowerCase (.contains "windows")))


(def SEP File/separator)
(def PSEP File/pathSeparator)


(def SHORTCUT_KEY (if IS_MAC "CMD" "CTRL"))

(defn newline-end? [seq-of-chars-or-string]
  (= \newline (last (seq seq-of-chars-or-string))))


(defn ensure-newline [obj]
  "ensures that the txt ends with a newline"
  (let [txt (if (nil? obj) "nil" (str obj))]
    (if (newline-end? txt)
      txt
      (str txt \newline))))


(defn clamp
  "low and high (both inclusive)"
  [low x high]
  ;(println "::clamp" low x high)
  (if (< x low) low
                (if (> x high) high
                               x)))

(defn in-range?
  "returns true if x is in range of low and high (both inclusive)"
  [low high x]
  (<= low  x high))


;(ns-unmap *ns* 'insert-at)
(defmulti insert-at
          "Returns a new vector or seq with xs inlined at offset.
          Ex.: (insert-at [1 2 3 4] 2 '(10 11)) ;-> [1 2 10 11 3 4]
          Ex.: (insert-at '(1 2 3 4) 2 '(10 11)) ;-> (1 2 10 11 3 4)"
          (fn [vec-or-coll offset xs] (class vec-or-coll)))

(defmethod insert-at PersistentVector [v offset xs]
  (vec (concat (subvec v 0 offset) xs (subvec v offset))))

(defmethod insert-at :default [coll offset xs]
  (let [[before after] (split-at offset coll)]
    (concat before xs after)))

;(ns-unmap *ns* 'replace-at)
(defmulti replace-at
          "Returns a new vector or seq with xs inlined in place of item at index.
          Ex.: (replace-at [1 2 3 4] 2 '(10 11)) ;-> [1 2 10 11 4]
          Ex.: (replace-at '(1 2 3 4) 2 '(10 11)) ;-> (1 2 10 11 4)"
          (fn [vec-or-coll index xs] (class vec-or-coll)))

(defmethod replace-at PersistentVector [v index xs]
  (vec (concat (subvec v 0 index) xs (subvec v (inc index)))))

(defmethod replace-at :default [coll index xs]
  (let [[before after] (split-at index coll)]
    (concat before xs (next after))))

;(ns-unmap *ns* 'replace-range)
(defmulti replace-range
          "Returns a new vector or seq with xs inlined in place of range start (inclusive) end (exclusive).
         Ex.: (replace-range [1 2 3 4] 1 3 '(10 11)) ;-> [1 10 11 4]
         Ex.: (replace-range '(1 2 3 4) 1 3 '(10 11)) ;-> (1 10 11 4)"
          (fn [vec-or-coll start end xs] (class vec-or-coll)))

(defmethod replace-range PersistentVector [v start end xs]
  (vec (concat (subvec v 0 start) xs (subvec v end))))

(defmethod replace-range :default [coll start end xs]
  (let [[before _] (split-at start coll)
        [_ after] (split-at (dec end) coll)]
    (concat before xs (next after))))

;(ns-unmap *ns* 'remove-at)
(defmulti remove-at
          "Returns a new vector or seq with item at index removed.
         Ex.: (remove-at [1 2 3 4] 2) ;-> [1 2 4]
         Ex.: (remove-at '(1 2 3 4) 2) ;-> (1 2 4)"
          (fn [vec-or-coll index] (class vec-or-coll)))

(defmethod remove-at PersistentVector [v index]
  (vec (concat (subvec v 0 index) (subvec v (inc index)))))

(defmethod remove-at :default [coll index]
  (let [[before after] (split-at index coll)]
    (concat before (next after))))


;(ns-unmap *ns* 'remove-range)
(defmulti remove-range
          "Returns a new vector or seq with  range start (inclusive) end (exclusive) removed.
         Ex.: (remove-range [1 2 3 4] 1 3) ;-> [1 4]
         Ex.: (remove-range '(1 2 3 4) 1 3) ;-> (1 4)"
          (fn [vec-or-coll start end] (class vec-or-coll)))

(defmethod remove-range PersistentVector [v start end]
  (vec (concat (subvec v 0 start) (subvec v end))))

(defmethod remove-range :default [coll start end]
  (let [[before _] (split-at start coll)
        [_ after] (split-at (dec end) coll)]
    (concat before (next after))))


;(println (insert-at [1 2 3 4] 2 '(10 11)))
;(println (insert-at '(1 2 3 4) 2 '(10 11)))
;(println (replace-at [1 2 3 4] 2 '(10 11)))
;(println (replace-at '(1 2 3 4) 2 '(10 11)))
;(println (replace-range [1 2 3 4] 1 3 '(10 11)))
;(println (replace-range '(1 2 3 4) 1 3 '(10 11)))
;(println (remove-at [1 2 3 4] 2))
;(println (remove-at '(1 2 3 4) 2))
;(println (remove-range [1 2 3 4] 1 3))
;(println (remove-range '(1 2 3 4) 1 3))
