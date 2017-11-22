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
    [george.util]  ;; important - to ensure loading of 'defmethod diff/patch Vector'
    [george.javafx.java :as j]
    [george.editor.state :as st]
    :reload
    [clojure.core.rrb-vector :as fv]
    [george.editor.buffer :as b]
    [clj-diff.core :as diff])
  (:import (javax.script ScriptEngineManager)
           (jdk.nashorn.api.scripting ScriptObjectMirror NashornScriptEngine)))


(set! *warn-on-reflection* true)
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

(defn new-formatter
  "Returns a function which takes the current state, and returns a new state ... I think.
  It will be synchronous, and it will operate on content directly.
  'strict?' forces \"paren-mode\" - for use when data has been pasted or read from file."

  []
  (fn [state strict? selection-start-line]
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
          (st/reset-prefcol_)
          (st/set-anchor_ res-anchor)
          st/ensure-derived_))))


;;; DEV ;;;



;(let [ires  (indent-mode "(def foo [a b")]
;  (prn "## ires:"  ires)
;  (prn "  ## text:" (ires :text)))
;
;(let [pres  (paren-mode "(def foo\n[a b\nc])")]
;  (prn "## pres:"  pres)
;  (prn "  ## text:" (pres :text)))

