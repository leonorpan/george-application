;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.editor.demos
  (:require
    [george.editor.core :as e]
    [george.javafx :as fx]
    [george.editor.buffer :as b]))


(defn- test-stage [view]
  (fx/now
    (fx/stage :title "Editor demo stage"
              :scene (fx/scene view :size [300 400]))))


(defn no-text-editor []
  (let []
    (test-stage (e/editor-view))))


(defn small-text-editor []
  (test-stage
    (e/editor-view
      "This is a text.\nThe quick brown fox jumped over ...\n ... the what?")))


(defn code-text-editor []
  (test-stage
    (e/editor-view (b/read-triangle-code))))


(defn large-text-editor []
  (let [view (e/editor-view (b/read-large-text))]
    ;(println "view's state_:" (.getState view))
    (test-stage view)))



;(no-text-editor)
;(small-text-editor)
;(code-text-editor)
;(large-text-editor)
