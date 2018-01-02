;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:docs "
  To get the newest  stable version of parinfer, allowing for all possible options and configurations, this module uses the original JavaScript implementation directly, in stead of a JVM (Clojure) version.
  For information details see: https://github.com/shaunlebron/parinfer/tree/master/lib"}

  george.editor.formatters.parinfer

  (:require
    [clojure.java.io :as cio]
    [clojure.data.json :as json]
    [clojure.pprint :refer [pp pprint]]
    [clj-diff.core :as diff]
    [george.util]  ;; important - to ensure loading of 'defmethod diff/patch Vector'
    [george.javafx.java :as j]
    [george.editor.state :as st]
    [george.editor.formatters.defs :as defs]
    [george.editor.buffer :as b]
    [george.util.text :as ut])

  (:import
    [javax.script ScriptEngineManager]
    [jdk.nashorn.api.scripting ScriptObjectMirror NashornScriptEngine]))


;(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defn- ^NashornScriptEngine new-js-engine []
  (let [factory (ScriptEngineManager.)
        engine  (.getEngineByName factory "JavaScript")]
    engine))


(defn- ^String read-parinferjs []
  (slurp (cio/resource "js/parinfer.js")))


(def ^NashornScriptEngine js-engine
  (doto
    (new-js-engine)
    (.eval  (read-parinferjs))))


(defn- eval-js [^String s]
    (.eval js-engine s))
;(println (eval-js "print('Hello, World')"))
;(println (eval-js "var x = 8; 2 + x;"))
;(println (eval-js "2+5; 3 + x;"))
;(println (eval-js "function println(s) { print(s+'\\n'); }"))
;(println (eval-js "println('println called. :-)');"))
;(println (eval-js "function adder(x, y) { return x + y; }"))
;(let [res (eval-js "adder(2, 3);")] (println "res:" res "type:" (type res)))


(defn- clojurize-kv
  "Recursively transforms all
  ScriptObjectMirror to maps,
  keys from strings to keywords, and doubles to ints"
  [kvs]
  (let [
        kf #(if (string? %) (keyword %) %)
        nf #(if (and (number? %) (= % (Math/floor %))) (int %) %)
        vf #(if (or (instance? ScriptObjectMirror %) (map? %)) (clojurize-kv %) (nf %))
        kvf (fn [[k v]] [(kf k) (vf v)])]
    (into {} (map kvf kvs))))


;; https://github.com/shaunlebron/parinfer/blob/master/lib/doc/integrating.md
;; TODO: implement TAB-handling - for single lines or selected multiple lines.
;; TODO: handle/highlight errors!
;; TODO: Investigate smartMode. Replace the other two and remove 'strict?'?
;; smartMode Demo: http://shaunlebron.github.io/parinfer/demo

(defn formatter*
  "A function which takes the current state, and returns a new state ... I think.
It will be synchronous, and it will operate on content directly.
'strict?' forces \"paren-mode\" - for use when data has been pasted or read from file."
  [state strict? selection-start-line]
  ;(println "formatter*")
  ;(println "parinfer/new-formatter/fn strict?:" strict? selection-start-line)
  (let [method-name (if strict? "parenMode" "indentMode")
        ^NashornScriptEngine engine js-engine

        buffer (st/buffer_ state)
        ;; lines may not have been updated
        text (String. (char-array buffer))
        [^int caret ^int anchor] (st/caret-anchor_ state)
        ;; we use 'mark' as a proxy, for "cursor", and then apply any changes to both caret and anchor
        mark (max caret anchor)

        lines (or (st/lines_ state) (b/split-buffer-lines buffer))
        [mrow mcol] (st/index->location-- lines mark)

        ops
        {
         "cursorLine" mrow
         "cursorX" mcol
         "selectionStartLine" (or selection-start-line mrow)}

        ops-json (json/write-str ops)

        ;; Get the native parinfer-object
        parinfer-js (.get engine "parinfer")

        ;; We need a reference the engine's JSON-object to convert our ops to a native structure
        JSON-js (.eval engine "JSON")
        ops-js (.invokeMethod engine JSON-js "parse" (j/vargs ops-json))

        res-js (.invokeMethod engine parinfer-js method-name (j/vargs-t Object text ops-js))
        res (clojurize-kv res-js)
        res-text (:text res)
        res-mark-pos [(:cursorLine res) (:cursorX res)]

        edits (diff/diff buffer (seq res-text))
        ;; Make sure to require [george.util] - is it loads the diff/patch implementation for Vector
        buffer (diff/patch buffer edits)

        state
        (-> state
            ;; 'constantly' simply returns a function which the already updated buffer.
            ;; (A hack to simply swap in the new buffer.)
            (st/update-buffer_ (constantly buffer))
            st/update-lines_)

        ^int res-mark (st/location->index_ state res-mark-pos)
        res-offset (- res-mark mark)
        res-caret (+ caret res-offset)
        res-anchor (+ anchor res-offset)]

    (-> state
        (st/set-caret_ res-caret)
        (st/set-anchor_ res-anchor)
        st/ensure-derived_)))


(defn- tabbable-rows_ [state]
  (let [[start end] (sort (st/caret-anchor_ state))
        [srow _] (st/index->location_ state start)
        [erow ecol] (st/index->location_ state end)]
    (if (= srow erow)
      [srow]
      (if (zero? ^int ecol)
        (range srow erow)
        (range srow (inc ^int erow))))))


(defn spaces-at-row-start-- [lines row]
  (let [line (lines row)]
    (loop [cnt 0 line line]
      (if-let [ch (first line)]
        (if (ut/space-char? ch)
          (recur (inc cnt) (rest line))
          cnt)
        cnt))))


(defn spaces
  "Returns a vector containing 'n' space-chars"
  [n]
  (vec (repeat n \space)))


;; # Alternative TAB-action
;; ## Cursive
;; Any marker on the first (or only) line always move with the line.
;; Any marker on the last line never move, if more than one row.
;; The last line moves only if the marker is col != 0.
;; If no selection, moves no matter where caret is in line.
;; ## IntelliJ
;; If no selection, caret moves line only when before first text, otherwise inserts tabe expanded to spaces.
;; Moves ending mark with line, if line moves.
;; ## Parinfer demo - http://shaunlebron.github.io/parinfer/demo
;; Inserts spaces like IntelliJ
;; Moves starting mark with line if no selection or if selection starting col != 0
;; Moves ending mark to beginning of line if line moves.
;; ## George
;; If the mark is on a line that moves then the mark moves!
;; If end-line>start-line, but end-mark at col=0 the end-line does not move.
;; Tab always moves line, never inserts spaces.


(defn- tab_ [state]
  ;(println "/tab_")
  (let [TAB 2
        lines (st/lines_ state) ;; Get lines, as first update will invalidate them.
        rows (tabbable-rows_ state)
        ;_ (println "tabbable-rows:" rows)

        ;; calculate insertion points and data: row -> [index items]
        insertions
        (map #(vector (st/location->index-- lines [% 0])  TAB) rows)

        ^int insertions-sum
        (reduce + (map second insertions))

        ;; apply insertions to state
        state
        (reduce (fn [state [i cnt]]
                  (st/update-buffer_ state st/insert-at i (spaces cnt)))
                state
                (reverse insertions)) ;; reverse to avoid offset-issues for following insertions

        [^int c ^int a] (st/caret-anchor_ state)
        caret-first? (<= c a)
        [^int crow _] (st/index->location-- lines c)
        [^int arow _] (st/index->location-- lines a)

        [c1 a1]
        (if (= crow arow)
          [(+ c TAB) (+ a TAB)]
          [(if caret-first?
             (+ c TAB)
             (+ c
                insertions-sum
                (if (zero? crow) (- TAB) 0)))
           (if (not caret-first?)
             (+ a TAB)
             (+ a
                insertions-sum
                (if (zero? arow ) (- TAB) 0)))])]

    [(st/set-marks_ state c1 true true a1) (first rows)]))


(defn test-code []
  (println "test-code"))
(def nothing "nothing")


(defn- clamp-untab--
  "Ensures that untab is not bigger than leading spaces. Adjusts nr of spaces to untab."
  [lines row ^long requested-dels]
  (let [^int spaces-cnt (spaces-at-row-start-- lines row)]
    (min spaces-cnt requested-dels)))


(defn- untab_ [state]
  ;; Algorithm:
  ;; Calculate possible untabs for each line, relative to the requested
  ;; Apply the untabs (in reverse).
  ;; Shift the start-marker the same as the first line
  ;; Sum the untabs and shift the end-tab - incl/excl tabbing if the end-line also was untabbed.

  ;; for the end-marker:
  ;; If I am at col0, then simply subtract all previous untabs.
  ;; Else
  ;; 1: What col am I at now.  Say 1 or 3
  ;; Subtract all previous untabs and current col to get col0.
  ;; Subtract all untabs, but do (max new-index col0-index)
  ;; su
  (let [UNTAB 2
        lines (st/lines_ state)
        rows (tabbable-rows_ state)

        deletions
        (map #(vector (st/location->index-- lines [% 0]) (clamp-untab-- lines % UNTAB)) rows)

        ;; apply deletions to state
        state
        (reduce (fn [state [^int i ^int cnt]]
                  (st/update-buffer_ state st/delete-range i (+ i cnt)))
                state
                (reverse deletions)) ;; reverse to avoid offset-issues for following insertions

        ^int deletions-sum
        (reduce + (map second deletions))

        [^int c ^int a] (st/caret-anchor_ state)
        caret-first? (<= c a)

        [^int crow ^int ccol] (st/index->location-- lines c)
        [^int arow ^int acol] (st/index->location-- lines a)

        ^int first-sum (-> deletions first second)
        ^int butlast-sum (reduce + (map second (butlast deletions)))

        start-col0-clamper
        (fn [^long index ^long col]
          (let [col0-index (- index  col)
                new-index (- index first-sum)]
            (max col0-index new-index)))

        end-col0-clamper
        (fn [^long index ^long col]
          (let [col0-index (- index butlast-sum col)
                new-index (- index deletions-sum)]
            (if (zero? col)
              (- index deletions-sum)
              (max col0-index new-index))))

        [^int c1 ^int a1]
        (if (= crow arow)
          [(start-col0-clamper c ccol)    (start-col0-clamper a acol)]
          (if caret-first?
            [(start-col0-clamper c ccol)  (end-col0-clamper a acol)]
            [(end-col0-clamper c ccol)    (start-col0-clamper a acol)]))]

    [(st/set-marks_ state c1 true true a1)
     (first rows)]))


(defn- tabber* [state typ]
  (if (= typ :tab)
    (tab_ state)
    (untab_ state)))


;(defmethod defs/type-test String [x]
;  (println "A string:" x))


(defmethod defs/formatter :clj [_]
  formatter*)

(defmethod defs/tabber :clj [_]
  tabber*)


;;; DEV ;;;


;(let [ires  (indent-mode "(def foo [a b")]
;  (prn "## ires:"  ires)
;  (prn "  ## text:" (ires :text)))
;
;(let [pres  (paren-mode "(def foo\n[a b\nc])")]
;  (prn "## pres:"  pres)
;  (prn "  ## text:" (pres :text)))

