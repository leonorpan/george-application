;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.output
  (:require [george.javafx :as fx]
            [george.code.codearea :as ca]
            [george.util.singleton :as singleton]
            [george.code.highlight :as highlight])
  (:import (java.io StringWriter PrintStream OutputStreamWriter)
           (org.apache.commons.io.output WriterOutputStream)
           (javafx.geometry Pos)
           (org.fxmisc.flowless VirtualizedScrollPane)))



(defonce standard-out System/out)
(defonce standard-err System/err)

(declare print-output)


(defn- output-area []
  (when-let [stage (singleton/get ::output-stage)]
    (->  stage .getScene .getUserData :output-area)))


(defn- output-string-writer [typ] ;; type is one of :out :err
  (proxy [StringWriter] []
    (flush []
      ;; first print the content of StringWriter to output-stage
      (let [s (str this)]
        (if (= typ :err)
          (.print standard-err s)
          (.print standard-out s))
        (print-output typ s))
      ;; then flush the buffer of the StringWriter
      (let [sb (.getBuffer this)]
        (.delete  sb 0 (.length sb))))))


(defn wrap-outs []
  (println "wrap-outs")
  (let [
        ow (output-string-writer :out)
        ew (output-string-writer :err)]

    (System/setOut (PrintStream. (WriterOutputStream. ow) true))
    (System/setErr (PrintStream. (WriterOutputStream. ew) true))
    (alter-var-root #'*out* (constantly ow))
    (alter-var-root #'*err* (constantly ew))))


(defn unwrap-outs []
  (System/setOut standard-out)
  (System/setErr standard-err)
  (alter-var-root #'*out* (constantly (OutputStreamWriter. System/out)))
  (alter-var-root #'*err* (constantly (OutputStreamWriter. System/err)))
  (println "unwrap-outs"))


(defn output-showing?
  "Used by eval to determine whether or not to show error in dialog."
  []
  (boolean (singleton/get ::output-stage)))


(defn- output-style [typ]
  ({:out "out"
    :err "err"
    :in "in"
    :ns "ns"
    :system "system"
    :res "res"} typ "unknown"))


(defn print-output [typ obj]  ;; type is one of :in :ns :res :out :err :system
  ;; TODO: maybe crop old lines from beginning for speed?
  (if-let [oa (output-area)]
    (fx/later
      (try
        (let [s (str obj)
              start (.getLength oa)
              end (+ start (count s))]
          (doto oa
            (.insertText start s) ;; append
            (.showParagraphAtBottom  (-> oa .getParagraphs count)) ;; scroll
            (highlight/set-style-on-range [start end] (output-style typ)))) ;; style
        (catch Exception e (unwrap-outs) (.printStackTrace e)))))

  ;; else:  make sure these always also appear in stout
  (when (#{:in :res :system} typ)
    (.print standard-out (str obj))))


(defn- output-scene []
  (let [
        codearea
        (doto (ca/new-codearea false)
          (.setStyle "-fx-font-size:14;")
          (.setEditable false))

        clear-button
        (fx/button
          "Clear"
          :width 150
          :onaction #(ca/set-text codearea "")
          :tooltip (format "Clear output"))

        button-box
        (fx/hbox

          (fx/region :hgrow :always)
          clear-button
          :spacing 3
          :alignment Pos/TOP_RIGHT
          :insets [0 0 5 0])

        scene
        (fx/scene (fx/borderpane
                    :top button-box
                    :center (VirtualizedScrollPane. codearea)
                    :insets 5))]

    (doto scene
      (.setUserData {:output-area codearea}))))



(defn- create-output-stage []
  (wrap-outs)
  (let [bounds (.getVisualBounds (fx/primary-screen))
        size [1000 300]]
    (fx/now
      (fx/stage
        :title "Output"
        :tofront true
        :location [(+ (.getMinX bounds) 20)
                   (- (-> bounds .getMaxY (- (second size))) 20)]
        :size size
        :sizetoscene false
        :scene (output-scene)
        :onhidden
        (fn []
          (unwrap-outs)
          (singleton/remove ::output-stage))))))




(defn show-or-create-output-stage []
  (if-let [stage (singleton/get ::output-stage)]
    (.toFront stage)
    (singleton/put ::output-stage (create-output-stage))))

