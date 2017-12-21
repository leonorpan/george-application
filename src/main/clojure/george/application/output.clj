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

(def LINE_COUNT_LIMIT 500)
(def LINE_COUNT_CROP_AT (int (* LINE_COUNT_LIMIT 1.2)))
(def OTA_KW ::output-textarea)
(def OS_KW ::output-stage)


(declare print-output)


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
  (boolean (singleton/get OTA_KW)))


(defn- output-style [typ]
  ({:out "out"
    :err "err"
    :in "in"
    :ns "ns"
    :system "system"
    :res "res"} typ "unknown"))


(defn- maybe-crop-output [outputarea]
  (let [cnt (count (.getParagraphs outputarea))]
    (when (> cnt LINE_COUNT_CROP_AT)
      ;(.print standard-out (format "CROP NOW! %s  %s\n" cnt  (range (- cnt LINE_COUNT_LIMIT))))
      (let [len (reduce +
                        (map #(inc (count (.getParagraph outputarea %)))
                             (range (- cnt LINE_COUNT_LIMIT))))]
        (.replaceText outputarea 0  len "")))))


(defn print-output [typ obj]  ;; type is one of :in :ns :res :out :err :system
  (if-let[oa (singleton/get OTA_KW)]
    (fx/later
      (maybe-crop-output oa)
      ;(try
      (let [s (str obj)]
        (when-not (empty? s)
          (let [start (.getLength oa)
                end (+ start (count s))]
            (doto oa
              (.insertText start s) ;; append
              (.showParagraphAtBottom  (-> oa .getParagraphs count)) ;; scroll
              (highlight/set-style-on-range [start end] (output-style typ)))))))) ;; style
        ;(catch Exception e (unwrap-outs) (.printStackTrace e)))))

  ;; else:  make sure these always also appear in stout
  (when (#{:in :res :system} typ)
    (.print standard-out (str obj))))


(defn- setup-output [ta]
  (singleton/put OTA_KW ta)
  (wrap-outs))


(defn teardown-output
  "Should be called when disposing of output-root - for maximal performance/efficiency."
  []
  (unwrap-outs)
  (singleton/remove OTA_KW))


(defn output-root []
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

        root
         (fx/borderpane
            :top button-box
            :center (VirtualizedScrollPane. codearea)
            :insets 5)]

    (setup-output codearea)

    root))


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