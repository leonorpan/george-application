;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "All functionality that touches the buffer somehow."}

  george.editor.buffer
  (:require
    [clojure.java.io :as cio]
    [clojure.data :as cd]
    [clj-diff.core :as diff]
    [george.util :as u])
  (:import (java.io PushbackReader StringReader)
           (javafx.collections ObservableList)))

;(set! *warn-on-reflection* true)
;(set! *unchecked-math* true)


(declare read-char unread-char peek-char)


(defn- return-char? [ch]
  (identical? \return ch))

(defn- newline-char? [ch]
  (identical? \newline ch))

(defn- formfeed-char? [ch]
  (identical? \formfeed ch))

(defn- newline-formfeed-char? [ch]
  (or (newline-char? ch) (formfeed-char? ch)))



(defn- normalized-newline
  "Returns a 2-part vector containing: [ch str-or-nil]
  where ch is a possibly the original char or a normalized newline
  and str-or-nil is the original newline-combo if a normalization occured"
  [^PushbackReader rdr ch]
  (if (return-char? ch)
    (let [ich (.read rdr)]
      (if (not= ich -1)
        (if (newline-formfeed-char? (char ich))
          [\newline (str \return (char ich))]
          (do (.unread rdr ich)
              [\newline "\r"]))
        [\newline "\r"]))
    [ch nil]))


(defn- ^PushbackReader pushback-stringreader [^String s]
  (PushbackReader. (StringReader. s)))



(defn- string->normalized->vec
  "Returns a 2-part vector containing the original newline-str and a vector or chars.
  Use the newline-str when writing (back) to file."
  [^String s]
  (loop [rdr (pushback-stringreader s)
         ich (.read rdr)
         chars (transient [])
         orig-nl "\n"]

    (if (not= ich -1)
      (let [[ch nl] (normalized-newline rdr (char ich))]
        (recur rdr (.read rdr) (conj! chars ch) (or nl orig-nl)))
      [orig-nl (persistent! chars)])))


(defn new-buffer
  "Returns a 2-part vector containing the buffer and the original newline-combo as str."
  [^String s]
  (let [[nl-str buf] (string->normalized->vec s)]
    [buf nl-str]))


(defn- line-start-indexes
  "Returns a vector of indexes representing the start of each line.
  Will return at a minimum '[0]' if 'sb' is empty or contains no '\n'"
  [^StringBuilder sb]
  (let [len (count sb)]
    (loop [start-index 0
           indexes [start-index]]
      (if (< start-index len)
        (let [i (.indexOf sb "\n" start-index)]
          (if (not= -1 i)
            (recur (inc i) (conj indexes (inc i)))
            indexes))
        indexes))))


(defn- split-lines
  "Divides the content of the StringBuilder 'sb' at the indexes.
  Returns a vector of Strings, starting at each index, til the end of 'sb', or the provided 'end'.
  'indexes' must contain at least 1 integer, and no integers may be bigger than length og sb or 'end'."
 ([^StringBuilder sb indexes]
  (split-lines sb indexes (count sb)))
 ([^StringBuilder sb indexes end]
  (let []
    (loop [lines [] [frst scnd & rst] indexes]
       (if scnd
         (recur
           (conj lines (.substring sb frst scnd)) (cons scnd rst))
         (conj lines (.substring sb frst end)))))))


(defn split-buffer-lines [buffer]
  ;(println "/split-buffer-lines" buffer)
  (loop [lines (transient [])
         line (transient [])
         [ch & rst] buffer]
    (if ch
      (if (newline-char? ch)
        (recur
          (conj! lines (persistent! (conj! line ch))) ;; add ch to current line and add it to lines
          (transient [])  ;; create a new line
          rst)
        (recur
          lines
          (conj! line ch) ;; add ch to current line
          rst))
      ;; add current line to lines and return lines
      (persistent! (conj! lines (persistent! line))))))


(defn insert-at [buffer offset chars]
  (u/insert-at buffer offset chars))

(defn replace-range [buffer start end chars]
  (u/replace-range buffer start end chars))

(defn delete-range [buffer start end]
  (u/remove-range buffer start end))



;;;;;;


(def DEL_SYM "LINE_TO_BE_DELETED")  ;; used as a marker for lines to be deleted


(defn do-update-list
  "Updates the observable-list  so it matches the buffer.
  It uses the Meyer's Diff Algorithm: http://simplygenius.net/Article/DiffTutorial1"

  [lines ^ObservableList list]
  (let [edit-script (diff/diff (vec list) lines)
        {additions :+ deletions :-} edit-script]
    ;; apply deletions
    (doseq [i deletions]
      (.set list i DEL_SYM))
    ;; apply additions
    ;(prn "  ## additions:" additions)
    (doseq [[i & items] additions]
      (loop [i (inc i) items items]
        (when-let [item (first items)]
          ;(prn "  ## item:" item)
          (.add list i item)
          (recur (inc i) (next items)))))
    ;; clean up
    (loop [removed-one (.remove list DEL_SYM)]
      (when removed-one
        ;(println "  ## removed 1 del-sym:" removed-one)
        (recur (.remove list DEL_SYM))))))


;;;;;;



(defn- test-linestarts []
  (let [sb (StringBuilder. ^String (slurp (cio/resource "texts/triangle.clj")))
        sb (StringBuilder. "")
        ;sb (time (StringBuilder. ^String (slurp (cio/resource "texts/text-180k.html"))))
        lines-at (time (line-start-indexes sb))
        lines (time (split-lines sb lines-at))]
    (println "lines-at:" lines-at)
    (println "line-count:" (count lines-at))
    (doseq [[i c l] (for [i (range (count lines-at))]
                      [i (lines-at i) (lines i)])]
           (prn i c l))))
;(test-linestarts)




(defn- test-linediffs []
  (let [l1 [0 2 4 6]
        l2 [0 3 5 7]
        l3 [0 1 2 3 7]
        l4 [0 1 2 4 7]
        r1 (cd/diff l1 l2)
        r2 (cd/diff l2 l3)
        r3 (cd/diff l3 l4)
        r4 (cd/diff [[0 0], [1 2], [2 4], [3 6]]
                    [[0 0], [1 1], [2 3], [3 5], [4 7]])]
    (println r1)
    (println r2)
    (println r3)))
    ;(println r4)))
;(test-linediffs)



(defn ^String read-resource [resource-filepath-str]
  (slurp (cio/resource resource-filepath-str)))

(defn read-triangle-code []
  (read-resource "texts/triangle.clj"))

(defn read-large-text []
  (read-resource "texts/text-180k.html"))

(defn bufferize-triangle []
  (string->normalized->vec (read-triangle-code)))
;(prn (time (bufferize-triangle)))

(defn bufferize-large-text []
  (string->normalized->vec (read-large-text)))
;(prn (time (bufferize-large-text)))


(defn split-bufferized-triangle []
  (split-buffer-lines (second (bufferize-triangle))))
;(prn (time (split-bufferized-triangle)))