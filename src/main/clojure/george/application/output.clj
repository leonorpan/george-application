;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.output
  (:require [george.javafx :as fx]
            [george.util.singleton :as singleton])
  (:import (java.io StringWriter PrintStream OutputStreamWriter)
           (org.apache.commons.io.output WriterOutputStream)
           (java.util Collections)
           (org.fxmisc.richtext StyledTextArea)
           (java.util.function BiConsumer)
           (javafx.scene.text Text)
           (javafx.geometry Pos)))




(defonce ^:private output-singleton (atom nil))

(defonce standard-out System/out)
(defonce standard-err System/err)

(def LINE_COUNT_LIMIT 500)
(def LINE_COUNT_CROP_AT (int (* LINE_COUNT_LIMIT 1.2)))
(def OTA_KW ::output-textarea)


(declare output)


(defn- output-string-writer [typ] ;; type is one of :out :err
  (proxy [StringWriter] []
    (flush []
      ;; first print the content of StringWriter to output-stage
      (let [s (str this)]
        (if (= typ :err)
          (.print standard-err  s)
          (.print standard-out s))
        (output typ s))
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
  (println "unwrap-outs")
  (System/setOut standard-out)
  (System/setErr standard-err)
  (alter-var-root #'*out* (constantly (OutputStreamWriter. System/out)))
  (alter-var-root #'*err* (constantly (OutputStreamWriter. System/err))))


(defn output-showing? []
  (boolean (singleton/get OTA_KW)))


(defrecord OutputStyleSpec [color])

(def ^:private DEFAULT_SPEC (->OutputStyleSpec "GRAY"))

(defn ^:private output-style [typ]
  (get {:out (->OutputStyleSpec "#404042")
        :err (->OutputStyleSpec "#CC0000")
        :in (->OutputStyleSpec "BLUE")
        :ns (->OutputStyleSpec "BROWN")
        :system (->OutputStyleSpec "#bbbbbb")
        :res (->OutputStyleSpec "GREEN")}
       typ
       (->OutputStyleSpec "ORANGE")))



(defn- maybe-crop-output [outputarea]
  (let [cnt (count (.getParagraphs outputarea))]
    (when (> cnt LINE_COUNT_CROP_AT)
      ;(.print standard-out (format "CROP NOW! %s  %s\n" cnt  (range (- cnt LINE_COUNT_LIMIT))))
      (let [len (reduce +
                        (map #(inc (count (.getParagraph outputarea %)))
                             (range (- cnt LINE_COUNT_LIMIT))))]
        (.replaceText outputarea 0  len "")))))



(defn output [typ obj]  ;; type is one of :in :ns :res :out :err :system
  (if-let[oa (singleton/get OTA_KW)]
    (fx/later
      (maybe-crop-output oa)
      (let [start (.getLength oa)]
        (.insertText oa start (str obj))
        (.setStyle oa start (.getLength oa) (output-style typ)))))
  ;; else:  make sure these always also appear in stout
  (when (#{:in :res :system} typ)
    (.print standard-out (str obj))))


(defn- style [spec]
  (let [{c :color} spec]
    (str
      "-fx-fill: " (if c c "#404042") "; "
      "-fx-font-weight: normal; "
      "-fx-underline: false; "
      "-fx-background-fill: null; ")))


(defn- apply-specs [^Text text specs]
  (cond
    (instance? OutputStyleSpec specs)
    (.setStyle text (style specs))

    (= Collections/EMPTY_LIST specs)
    (.setStyle text (style DEFAULT_SPEC))

    :default
    (doseq [spec specs]
      (.setStyle text (style spec)))))


(defn- style-biconsumer []
  (reify BiConsumer
    (accept [_ text style]
      (apply-specs text style))))



(defn- setup-output [ta]
  (singleton/put OTA_KW ta)
  (wrap-outs))


(defn teardown-output
  "Should be called when disposing of output-root - for maximal performacne/efficiency."
  []
  (unwrap-outs)
  (singleton/remove OTA_KW))



(defn output-root []
  (let [
        outputarea
        (doto (StyledTextArea. DEFAULT_SPEC (style-biconsumer))
          (.setFont (fx/SourceCodePro "Regular" 14))
          (.setStyle "-fx-padding: 0; -fx-background-color: WHITESMOKE;")
          (.setUseInitialStyleForInsertion true)
          (-> .getUndoManager .forgetHistory)
          (-> .getUndoManager .mark)
          (.selectRange 0 0)
          (.setEditable false))

        clear-button
        (fx/button
          "Clear"
          :width 150
          :onaction #(.replaceText outputarea "")
          :tooltip (format "Clear output"))

        button-box
        (fx/hbox
          (fx/region :hgrow :always)
          clear-button
          :spacing 3
          :alignment Pos/TOP_RIGHT
          :insets [0 0 5 0])

        root
         (fx/borderpane
            :top button-box
            :center outputarea
            :insets 5)]

    (setup-output outputarea)

    root))



(def OS_KW ::output-stage)


(defn create-stage
 ([]
  (create-stage (output-root)))

 ([root]
  (let [bounds (.getVisualBounds (fx/primary-screen))
        size [1000 330]]
    (fx/now
      (fx/stage
        :title "Output"
        :location [(.getMinX bounds)
                   (- (.getMaxY bounds)  (second size))]
        :size size
        :sizetoscene false
        :scene (fx/scene root)
        :onhidden #(do (teardown-output)
                       (singleton/remove OS_KW)))))))


(defn show-or-create-stage []
  (println "output/show-or-create-stage")
  (doto
    (singleton/get-or-create OS_KW create-stage)
    (.toFront)))


;; TODO:  make into split-screen!