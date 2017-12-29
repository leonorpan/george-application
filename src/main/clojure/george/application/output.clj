; Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns george.application.output
  (:require
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.code.codearea :as ca]
    [george.util.singleton :as singleton]
    [george.code.highlight :as highlight]
    [george.application
     [repl :as client]
     [repl-server :as server]])
  (:import
    [java.io StringWriter PrintStream OutputStreamWriter]
    [org.apache.commons.io.output WriterOutputStream]
    [javafx.geometry Pos Side]
    [javafx.scene.control MenuButton MenuItem SeparatorMenuItem]
    [org.fxmisc.flowless VirtualizedScrollPane]
    (org.fxmisc.richtext StyleClassedTextArea)
    (clojure.lang Keyword)))


(declare oprint)


(defonce standard-out System/out)
(defonce standard-err System/err)

;; To avoid cropping continuously, we wait til we are 20% over the limit, then crop to the limit.
(def LINE_COUNT_LIMIT 500)
(def LINE_COUNT_CROP_AT (int (* LINE_COUNT_LIMIT 1.2)))

;; Keywords used for singletons.
(def OTA_KW ::output-textarea)


(defn- output-string-writer [typ] ;; type is one of :out :err
  (proxy [StringWriter] []
    (flush []
      ;; first print the content of StringWriter to output-stage
      (let [s (str this)]
        (if (= typ :err)
          (.print standard-err s)
          (.print standard-out s))
        (oprint typ s))
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
    :system-em "system-em"
    :res "res"} typ "unknown"))


(defn- maybe-crop-output [outputarea]
  ;; Count number of lines
  (let [cnt (count (.getParagraphs outputarea))]
    (when (> cnt LINE_COUNT_CROP_AT)
      ;; Get the number of chars in each paragraph with 'map', and sum them up with 'reduce'
      (let [len (reduce +
                        (map #(inc (.getParagraphLenth outputarea %))  ;; Yes, a typo in the method name.
                             (range (- cnt LINE_COUNT_LIMIT))))]
        (.replaceText outputarea 0  len "")))))


(defn- print-output* [typ obj]  ;; type is one of :in :ns :res :out :err :system
  (if-let [^StyleClassedTextArea oa (singleton/get OTA_KW)]
    (fx/later
      (maybe-crop-output oa)
      ;(try)
      (let [s (str obj)]
        (when-not (empty? s)
          (let [start (.getLength oa)]
            (doto oa
              (.insertText start s) ;; append
              (.showParagraphAtBottom (-> oa .getParagraphs count)) ;; scroll
              (highlight/set-style-on-range
                [start (.getLength oa)]
                (output-style typ)))))))) ;; style
      ;(catch Exception e (unwrap-outs) (.printStackTrace e))))

  ;; else:  make sure these always also appear in stout
  (when (#{:in :res :system :system-em} typ)
    (.print standard-out (str obj))))


(defn oprint
  "typ is one of :in :ns :res :out :err :system :system-em"
 ([] nil)
 ([^Keyword _] nil)
 ([^Keyword typ obj & more]
  (when-not (keyword? typ)
    (throw (IllegalArgumentException. "First argument to vprint/oprintln must be keyword")))
  (print-output* typ obj)
  (when-let [obj2 (first more)]
    (print-output* typ " ")
    (recur typ obj2 (rest more)))))


(defn oprintln
 ([] (println))
 ([^Keyword _] (println))
 ([^Keyword typ obj & more]
  (apply oprint (cons typ (cons obj more)))
  (println)))


(defn- setup-output [ta]
  (singleton/put OTA_KW ta)
  (wrap-outs))


(defn teardown-output
  "Should be called when disposing of output-root - for maximal performance/efficiency."
  []
  (unwrap-outs)
  (singleton/remove OTA_KW))


(defn style-bar [bar]
  (doto bar
    (.setStyle "-fx-background-color: #eee; -fx-padding: 3;")))


(defn ping-sessions []
  (oprintln :system "Ping sessions ...")
  (if-not (server/serving?)
    (oprintln :system-em "  No server started!")
    (let [current-ses (client/session)
          sessions (client/sessions)]
      (doseq [ses sessions]
        (let [ok? (client/ping ses)]
          (oprint :system "  " ses "")
          (if ok? (oprint :system-em "OK")
                  (oprint :err "Fail!"))
          (oprintln  :system "" (when (= ses current-ses) " [default]")))))))


(defn interrupt-sessions []
  (oprintln :system "Interrupt sessions ...")
  (if-not (server/serving?)
    (oprintln :system-em "  No server started!")
    (let [sessions (client/sessions)]
      (doseq [ses sessions]
        (let [interupted? (client/interrupt ses)]
          (oprint :system "  " ses "")
          (if interupted?
            (oprintln :system-em "Interrupted!")
            (oprintln :system "Idle")))))))


(defn recreate-session []
  (if-not (server/serving?)
    (do
      (server/serve! 0)
      (oprintln :system "New server on port" (server/port))
      (let [id (client/session-create!)]
        (oprintln :system "New default session" id)))
    (do
      (interrupt-sessions)
      (when (client/session?)
        ;(client/interrupt)
        ;(Thread/sleep 300)
        (client/session-close!))
      (let [id (client/session-create!)]
        (oprintln :system "New default session" id)))))


(defn restart-server []
  (interrupt-sessions)
  (server/stop!)
  (server/serve! 0)
  (oprintln :system "New server on port" (server/port))
  (recreate-session))


(defn output-root []
  (let [codearea
        (doto (ca/new-codearea false)
          (.setStyle "-fx-font-size:14;")
          (.setEditable false)
          (.setFocusTraversable true))

        clear-button
        (fx/button
          "Clear"
          ;:width 150
          :onaction #(ca/set-text codearea "")
          :tooltip "Clear output")

        top-bar
        (fx/hbox
          (fx/region :hgrow :always)
          clear-button
          :spacing 3
          :alignment Pos/TOP_RIGHT
          :insets [0 0 5 0])

        bottom-bar
        (fx/hbox
          (fx/region :hgrow :always)
          (doto (MenuButton. "nREPL"
                             nil
                             (fxj/vargs
                               (doto (MenuItem. "Ping all sessions")
                                 (fx/set-onaction #(ping-sessions)))
                               (doto (MenuItem. "Interrupt all sessions!")
                                 (fx/set-onaction #(interrupt-sessions)))
                               (SeparatorMenuItem.)
                               (doto (MenuItem. "Create new default session")
                                     (fx/set-onaction #(recreate-session)))
                               (SeparatorMenuItem.)
                               (doto (MenuItem. "Start new server")
                                     (fx/set-onaction #(restart-server)))))
                (.setPopupSide Side/TOP))
          :spacing 5)
        root
        (fx/borderpane
           :top (style-bar top-bar)
           :center (VirtualizedScrollPane. codearea)
           :bottom (style-bar bottom-bar)
           :insets 5)]

    (setup-output codearea)
    [root clear-button]))
