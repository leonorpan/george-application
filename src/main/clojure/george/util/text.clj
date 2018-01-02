;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.util.text
  (:require
    [clojure.pprint :as pprint]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defn return-char? [ch]
  (identical? \return ch))


(defn newline-char? [ch]
  (identical? \newline ch))


(defn formfeed-char? [ch]
  (identical? \formfeed ch))


(defn newline-formfeed-char? [ch]
  (or (newline-char? ch) (formfeed-char? ch)))


(defn space-char? [ch]
  (identical? \space ch))


(defn tab-char? [ch]
  (identical? \tab ch))


(def coll-delim-chars #{\{ \[ \( \) \] \}})


(defn coll-delim-char? [ch]
  (coll-delim-chars ch))


(defn coll-delim-char-match
  "Returns the coll-delim-char which matches the passed-in char, else nil"
  [ch]
  (case ch
    \( \)
    \) \(
    \[ \]
    \] \[
    \{ \}
    \} \{
    nil))


(defn coll-delim-char-matches? [ch1 ch2]
  (= (coll-delim-char-match ch1) ch2))


(defn newline-end? [seq-of-chars-or-string]
  (= \newline (last (seq seq-of-chars-or-string))))


(defn ensure-newline [obj]
  "ensures that the txt ends with a newline"
  (let [txt (if (nil? obj) "nil" (str obj))]
    (if (newline-end? txt)
      txt
      (str txt \newline))))


(defn ppstr
  "returns the data as a pprint-ed str"
  [data]
  (pprint/write data :stream nil))
