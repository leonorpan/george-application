;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.editor.demos
  (:require
    [george.editor.core :as e]
    [george.javafx :as fx]
    [george.editor.buffer :as b]
    [george.application.ui.styled :as styled]))


(defn- test-stage [view]
  (fx/now
    (styled/style-stage
      (fx/stage :title "Editor demo stage"
                :scene (fx/scene view :size [400 400])
                :alwaysontop true))))


(defn no-text-editor []
  (let []
    (test-stage (e/editor-view))))


(defn small-text-editor []
  (test-stage
    (e/editor-view
      "This is a text.\nThe quick brown fox jumped over ...\n ... the what?")))


(defn sample-code-editor []
  (test-stage
    (e/editor-view
      "(defn foo\n  \"hello, this is a docstring\"\n  [a b]\n  (let [sum (+ a b)\n        prod (* a b)]\n     {:sum sum\n      :prod prod}))"
      :clj)))

(defn triangle-code-editor []
  (test-stage
    (e/editor-view
      (b/read-triangle-code) "clj")))


(defn large-text-editor [& [content-type]]
  (let [view (e/editor-view (b/read-large-text) content-type)]
    ;(println "view's state_:" (.getState view))
    (test-stage view)))


;(no-text-editor)
;(small-text-editor)
;(sample-code-editor)
;(triangle-code-editor)
;(large-text-editor)
;(large-text-editor :clj)