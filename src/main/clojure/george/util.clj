;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.util
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.core.rrb-vector :as fv]
    [clj-diff.core :as diff]
    [clojure.pprint :as cpp])
  (:import (java.util UUID Collection)
           (java.io File)
           (clojure.lang PersistentVector)
           (clojure.core.rrb_vector.rrbt Vector)
           (javafx.collections ObservableList)))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
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


;(defn clamp
;  "low and high (both inclusive)"
;  [low  x  high]
;  ;(println "::clamp" low x high)
;  (if (< x low)
;      low
;      (if (> x high)
;        high
;        x)))


(defn clamp-int
  "low and high (both inclusive)"
  [low  x  high]
  (max ^int low (min ^int x ^int high)))


(defn clamp-double
  "low and high (both inclusive)"
  [low  x  high]
  (max ^double low (min ^double x ^double high)))


(defn in-range?
  "returns true if x is in range of low and high (both inclusive)"
  [low high x]
  (<= low  x high))


;(ns-unmap *ns* 'insert-at)
(defmulti insert-at
          "Returns a new vector or seq with xs inlined at offset.
          Implemented for PersistentVector, clojure.core.rrb_vector.rrbt.Vector, collection (default).
          Ex.: (insert-at [1 2 3 4] 2 '(10 11)) ;-> [1 2 10 11 3 4]
          Ex.: (insert-at '(1 2 3 4) 2 '(10 11)) ;-> (1 2 10 11 3 4)"
          ;(fn [vec-or-coll offset xs] (class vec-or-coll))
          (fn [vec-or-coll offset xs] (class vec-or-coll)))


(defmethod insert-at Vector [v offset xs]
  ;(prn "  ## insert-at Vector:" v offset xs)
  (fv/catvec (fv/subvec v 0 offset) xs (fv/subvec v offset)))

(defmethod insert-at PersistentVector [v offset xs]
  (vec (concat (subvec v 0 offset) xs (subvec v offset))))

(defmethod insert-at :default [coll offset xs]
  (let [[before after] (split-at offset coll)]
    (concat before xs after)))


;(ns-unmap *ns* 'replace-range)
(defmulti replace-range
          "Returns a new vector or seq with xs inlined in place of range start (inclusive) end (exclusive).
          Implemented for PersistentVector, clojure.core.rrb_vector.rrbt.Vector, collection (default).
          Ex.: (replace-range [1 2 3 4] 1 3 '(10 11)) ;-> [1 10 11 4]
          Ex.: (replace-range '(1 2 3 4) 1 3 '(10 11)) ;-> (1 10 11 4)"
          (fn [vec-or-coll start end xs] (class vec-or-coll)))

(defmethod replace-range Vector [v start end xs]
  (fv/catvec (fv/subvec v 0 start) xs (fv/subvec v end)))

(defmethod replace-range PersistentVector [v start end xs]
  (vec (concat (subvec v 0 start) xs (subvec v end))))

(defmethod replace-range :default [coll start end xs]
  (let [[before _] (split-at start coll)
        [_ after] (split-at (dec ^int end) coll)]
    (concat before xs (next after))))

;(ns-unmap *ns* 'replace-at)
(defmulti replace-at
          "Returns a new vector or seq with xs inlined in place of item at index.
          Implemented for PersistentVector, clojure.core.rrb_vector.rrbt.Vector, collection (default).
          Ex.: (replace-at [1 2 3 4] 2 '(10 11)) ;-> [1 2 10 11 4]
          Ex.: (replace-at '(1 2 3 4) 2 '(10 11)) ;-> (1 2 10 11 4)"
          (fn [vec-or-coll index xs] (class vec-or-coll)))

(defmethod replace-at Vector [v index xs]
  (replace-range v index (inc ^int index) xs))

(defmethod replace-at PersistentVector [v index xs]
  (replace-range v index (inc ^int index) xs))

(defmethod replace-at :default [coll index xs]
  (replace-range coll index (inc ^int index) xs))

;(ns-unmap *ns* 'remove-range)
(defmulti remove-range
          "Returns a new vector or seq with  range start (inclusive) end (exclusive) removed.
          Implemented for PersistentVector, clojure.core.rrb_vector.rrbt.Vector, collection (default).
          Ex.: (remove-range [1 2 3 4] 1 3) ;-> [1 4]
          Ex.: (remove-range '(1 2 3 4) 1 3) ;-> (1 4)"
          (fn [vec-or-coll start end] (class vec-or-coll)))

(defmethod remove-range Vector [v start end]
  (fv/catvec (fv/subvec v 0 start) (fv/subvec v end)))

(defmethod remove-range PersistentVector [v start end]
  (vec (concat (subvec v 0 start) (subvec v end))))

(defmethod remove-range :default [coll start end]
  (let [[before _] (split-at start coll)
        [_ after] (split-at (dec ^int end) coll)]
    (concat before (next after))))


;(ns-unmap *ns* 'remove-at)
(defmulti remove-at
          "Returns a new vector or seq with item at index removed.
          Implemented for PersistentVector, clojure.core.rrb_vector.rrbt.Vector, collection (default).
          Ex.: (remove-at [1 2 3 4] 2) ;-> [1 2 4]
          Ex.: (remove-at '(1 2 3 4) 2) ;-> (1 2 4)"
          (fn [vec-or-coll index] (class vec-or-coll)))

(defmethod remove-at Vector [v index]
  (remove-range v index (inc ^int index)))

(defmethod remove-at PersistentVector [v index]
  (remove-range v index (inc ^int index)))

(defmethod remove-at :default [coll index]
  (remove-range coll index (inc ^int index)))


;(prn (insert-at (fv/vector 1 2 3 4) 2 [\a]))
;(prn (replace-at (fv/vector 1 2 3 4) 2 [\a]))
;(println (time (insert-at (fv/vector 1 2 3 4) 2 (fv/vector 10 11))))
;(println (time (insert-at [1 2 3 4] 2 '(10 11))))
;(println (insert-at '(1 2 3 4) 2 '(10 11)))
;(println (replace-at [1 2 3 4] 2 '(10 11)))
;(println (replace-at '(1 2 3 4) 2 '(10 11)))
;(println (replace-range [1 2 3 4] 1 3 '(10 11)))
;(println (replace-range '(1 2 3 4) 1 3 '(10 11)))
;(println (remove-at [1 2 3 4] 2))
;(println (remove-at '(1 2 3 4) 2))
;(println (remove-range [1 2 3 4] 1 3))
;(println (remove-range '(1 2 3 4) 1 3))



;(defn char-sequence
;  [seq-of-chars]
;  (reify
;    CharSequence
;    (length [_]
;      ^int (count seq-of-chars))
;    (charAt [_ ^int index] ;; This one doesn't compile!
;      ^char (nth seq-of-chars index))
;    (subSequence [_ ^int start ^int end]
;      (char-sequence
;        (cond
;          (instance? Vector seq-of-chars)
;          (fv/subvec  seq-of-chars start end)
;
;          (vector? seq-of-chars)
;          (subvec  seq-of-chars start end)
;
;          ;; default
;          (first (split-at (- end start) (second (split-at start seq-of-chars)))))))
;    ^String
;    (toString [_]
;      ;; TODO: Can we do better?!
;      (String. (char-array seq-of-chars)))))
;    ;; TODO: 'chars' and 'codePoints' should probably be implemented ...

;(def chars '(\a \b \c \d \e))
;(println chars)
;(println (.charAt (char-sequence chars) 1))
;(println (.subSequence (char-sequence '(\a \b \c \d \e)) 1 3))



;; Used as a marker for elements to be deleted.
;; Must be seq of chars, so as not to cause trouble in case it is temporarily rendered.
(def DEL_OBJ (seq "DEL_OBJ"))


(defmethod diff/patch Vector [v edit-script]
  ;(println "diff/patch Vector")
  (let [{adds :+ dels :-} edit-script
        adds (reverse adds)
        ;; Reverse because the insertion indexes don't take into account that
        ;; the previous insertion offsets what follows.
        ;; By starting at the back, it has no effect on the indexes before.

        v ;;apply deletions  (Mark elements to delete with DEL_OBJ)
        (reduce (fn [v i] (replace-at v i (fv/vector DEL_OBJ))) v dels)
        v ;; apply additions
        (reduce (fn [v add]
                    (insert-at v
                               (inc ^int (first add)) ;; increment because of how 'insert-at' works.
                               (fv/vec (rest add))))
                v adds)
        v  ;; clean up (remove DEL_OBJs)
        (if (empty? dels)  ;; Optimization: Don't bother looking.
            v
            (let [find-start (first dels) ;; Optimization: Start at index of first DEL_OBJ.
                  find-limit (count dels)] ;; Optimization: Stop once all DEL_OBJs are found.
              (loop [v v
                     [i & ix] (range find-start (count v)) ;; We need an index for getting elements
                     find-cnt 0]
                (if (= find-cnt find-limit)  ;; We found them all. We're done!
                  v
                  (let [item (get v i)]
                    (if (= item DEL_OBJ)
                      (recur (remove-at v i) (cons i ix) (inc find-cnt))
                      (recur v ix find-cnt)))))))]

    v))



(defn- fv-diffpatch-test
  "Testing diff-patch for fast vector"
  []
  (let [a (fv/vec "This is a test. aØSLDKJaøsdlkjaSØDLKJasdølakjDSØalksdjaøLSDKJaøsdlkj")
        b (fv/vec "Thiz was tested a lot. aØSLDKJaøsdlkjaSØDLKJasdølakjDSØalksdjaøLSDKJaøsdlkj")
        edit-script (diff/diff a b)
        r (diff/patch a edit-script)]
    ;(prn "  ## a:" a)
    ;(prn "  ## b:" b)
    ;(prn "  ## r:" r)))
    r))
;(prn (apply str (time (fv-diffpatch-test))))


(defn split-to-continuous-range-pairs
  "Returns a list of vectors, where each vector contains the first and last number of the ontinous range.
  The list of ranges is in reverse order.
  Ex.: [1 2  3 5  7 8] -> ([7 8] [5 5] [1 3])"
  [seq-of-ints]
  (reduce
    (fn [[cur & res] i]
        (if-let [[f l] cur]
                (if (= i (inc ^int l))
                    (cons [f i] res)
                    (cons [i i] (cons [f l] res)))
                (cons [i i] res)))
    (list)
    seq-of-ints))


(defmethod diff/patch ObservableList [^ObservableList olist {adds :+ dels :- :as edit-script}]
  ;(println "diff/patch ObservableList")

  ;; shortcut
  (if (empty? adds)
    (doseq [[^int f ^int l]
            (split-to-continuous-range-pairs dels)] (.remove olist f (inc l)))

    (let []
      ;; apply deletions
      (doseq [^int i dels] (.set olist i DEL_OBJ))
      ;; apply additions
      (doseq [[^int i & ^Collection items] (reverse adds)] (.addAll olist (inc i) items))
      ;; clean up
      (when-not (empty? dels)
        (let [find-start (first dels)
              find-limit (count dels)]
          (loop [ix (range find-start (count olist))
                 find-cnt 0]
            (when-not (= find-cnt find-limit)
              (if (identical? (.get olist (int (first ix))) DEL_OBJ)
                (do (.remove olist (int (first ix)))
                    (recur ix (inc find-cnt)))
                (recur (rest ix) find-cnt))))))))
  olist)


;(defn- olist-diffpatch-test
;  "Testing diff-patch for ObservableList"
;  []
;  (let [a (javafx.collections.FXCollections/observableArrayList (seq "This is a test. aØSLDKJaøsdlkjaSØDLKJasdølakjDSØalksdjaøLSDKJaøsdlkj"))
;        _ (prn "  ## a:" a)
;        b (fv/vec "Thiz was tested a lot. aØSLDKJaøsdlkjaSØDLKJasdølakjDSØalksdjaøLSDKJaøsdlkj")
;        _ (prn "  ## b:" b)
;        edit-script (diff/diff a b)
;        r (diff/patch a edit-script)
;        _ (prn "  ## r:" r)]
;    r))
;(prn (apply str (time (olist-diffpatch-test))))
