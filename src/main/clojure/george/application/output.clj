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
    [javafx.scene.control MenuButton MenuItem]
    [org.fxmisc.flowless VirtualizedScrollPane]))


(declare sprint)


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
        (sprint typ s))
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
  ;; Count number of lines
  (let [cnt (count (.getParagraphs outputarea))]
    (when (> cnt LINE_COUNT_CROP_AT)
      ;; Get the number of chars in each paragraph with 'map', and sum them up with 'reduce'
      (let [len (reduce +
                        (map #(inc (.getParagraphLenth outputarea %))  ;; Yes, a typo in the method name.
                             (range (- cnt LINE_COUNT_LIMIT))))]
        (.replaceText outputarea 0  len "")))))


(defn- print-output* [typ obj]  ;; type is one of :in :ns :res :out :err :system
  (if-let [oa (singleton/get OTA_KW)]
    (fx/later
      (maybe-crop-output oa)
      (try
        (let [s (str obj)]
          (when-not (empty? s)
            (let [start (.getLength oa)]
              (.insertText oa start s) ;; append
              (let [end (+ start (count s))]
                (doto oa
                  (.showParagraphAtBottom (-> oa .getParagraphs count)) ;; scroll
                  (highlight/set-style-on-range
                    [start (.getLength oa)]
                    (output-style typ))))))) ;; style
        (catch Exception e (unwrap-outs) (.printStackTrace e)))))

  ;; else:  make sure these always also appear in stout
  (when (#{:in :res :system} typ)
    (.print standard-out (str obj))))


(defn sprint
  "typ is one of :in :ns :res :out :err :system"
 ([typ]
  nil)
 ([typ obj & more]
  (when-not (keyword? typ)
    (throw (IllegalArgumentException. "First argument to sprint/sprintln must be keyword")))
  (print-output* typ obj)
  (when-let [obj2 (first more)]
    (print-output* typ " ")
    (recur typ obj2 (rest more)))))

(defn sprintln
 ([typ]
  (println))
 ([typ obj & more]
  (apply sprint (cons typ (cons obj more)))
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


(defn recreate-session []
  (when (client/session?)
    (client/session-close!))
  (let [id (client/session-create!)]
    (sprintln :system "nREPL session recreated with id" id)))


(defn restart-server []
   (server/stop!)
   (server/serve! 0)
   (sprintln :system "nREPL server started on port" (server/port))
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
          :tooltip (format "Clear output"))

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
                               (doto (MenuItem. "Restart nREPL server")
                                     (fx/set-onaction #(restart-server)))
                               (doto (MenuItem. "New nREPL session")
                                     (fx/set-onaction #(recreate-session)))))
                (fx/set-tooltip "All old session data will be lost if either menu selection is made!")
                (.setPopupSide Side/TOP))
          :spacing 5)
        root
        (fx/borderpane
           :top (style-bar top-bar)
           :center (VirtualizedScrollPane. codearea)
           :bottom (style-bar bottom-bar)
           :insets 5)]

    (setup-output codearea)

    root))
