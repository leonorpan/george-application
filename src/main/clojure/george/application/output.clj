;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.output
  (:require
    [george.javafx :as fx]
    [george.code.codearea :as ca]
    [george.util
     [singleton :as singleton]
     [text :as gt]]
    [george.code.highlight :as highlight]
    [george.application
     [repl :as client]
     [repl-server :as server]]
    [george.application.ui.layout :as layout]
    [clojure.string :as cs]
    [george.util.java :as j])
  (:import
    [java.io StringWriter PrintStream OutputStreamWriter]
    [org.apache.commons.io.output WriterOutputStream]
    [org.fxmisc.flowless VirtualizedScrollPane]
    [org.fxmisc.richtext StyleClassedTextArea]
    [clojure.lang Keyword]
    (javafx.scene.paint Color)))


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
    :res "res"} 
   typ 
   "unknown"))


(def 
  ^{:private true
    :doc "Hold one keyword pr line, indicating how the gutter for the line should be styled/marked.
    It is appended by 'update-gutter-types', and cropped by 'maybe-crop-output'.
    'curr' is the latest opened line. It may get updated until it is moved to prev.
    'prevs' holds one keyword pr all previous lines."}
  ;; We are assuming there is only one Output-window, so it is OK to have this as a global.
  gutter-types (atom {:prevs [] :curr nil}))


(defn- maybe-crop-output [outputarea]
  ;; Count number of lines
  (let [cnt (count (.getParagraphs outputarea))]
    (when (> cnt LINE_COUNT_CROP_AT)
      ;; Get the number of chars in each paragraph with 'map', and sum them up with 'reduce'
      (let [len (reduce +
                        (map #(inc (.getParagraphLength outputarea %))
                             (range (- cnt LINE_COUNT_LIMIT))))]
        (.replaceText outputarea 0  len "")
        (let [cnt1 (count (.getParagraphs outputarea))
              vlen (count (:prevs @gutter-types))]
          (swap! gutter-types 
                 #(assoc % :prevs (subvec (:prevs %) (- vlen cnt1 -1)))))))))


(defn- update-gutter-types [s typ]
  (swap! gutter-types assoc :curr typ)
  (loop [cs (seq s)]
    (when-let [c (first cs)]
      (when (gt/newline-char? c)
        (swap! gutter-types (fn [{:keys [prevs curr]}] 
                              {:prevs (conj prevs curr) 
                               :curr curr}))) 
      (recur (next cs)))))


(defn- marking-for-typ [typ]
  (get 
    {:err "!!"
     :ns "ns"
     :in "<<"
     :res "=>"
     :system "  "
     :system-em "  "} typ "  "))


(defn colors-for-typ [typ]
  (get {:err "firebrick"
        :ns "sienna"
        :in  "blue" 
        :res "green" 
        :system "#aaa" 
        :system-em "#999"} typ "#2b292e"))


(def gutter-style-f "
-fx-label-padding: 0 8;
-fx-text-fill: %s;
-fx-background-color: %s;
-fx-border-color: %s;
-fx-border-width: 0 1 0 0;
-fx-border-insets: 0 8 0 0;
-fx-background-insets: 0 8 0 0;")


(defn gutter-factory []
  (j/intfunction #(let [typ (get (:prevs @gutter-types) %)
                        col1 (colors-for-typ typ) 
                        col2 (if (= typ :err) "pink" "whitesmoke")
                        col3 (if (= typ :err) col1 "gainsboro")
                        style (format gutter-style-f col1 col2 col3)]    
                    (doto (fx/new-label (marking-for-typ typ)
                                        :color Color/WHITE
                                        :font "Source Code Pro"
                                        :size 16
                                        :style style)))))


(defn- print-output* [typ obj]  ;; type is one of :in :ns :res :out :err :system
  (if-let [^StyleClassedTextArea oa (singleton/get OTA_KW)]
    (fx/later
      (maybe-crop-output oa)
      ;(try)
      (let [s (str obj)]
        (update-gutter-types s typ)
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
 ;([] (println))
 ([^Keyword typ] (print-output* typ "\n"));(println))
 ([^Keyword typ obj & more]
  (apply oprint (cons typ (cons obj more)))
  (oprintln typ)))


(defn- setup-output [ta]
  (singleton/put OTA_KW ta)
  (wrap-outs))


(defn teardown-output
  "Should be called when disposing of output-root - for maximal performance/efficiency."
  []
  (unwrap-outs)
  (singleton/remove OTA_KW))


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


(defn interrupt-all-sessions []
  (oprintln :system "Interrupt all sessions ...")
  (if-not (server/serving?)
    (oprintln :system-em "No server started!")
    (let [sessions (client/sessions)]
      (doseq [ses sessions]
        (let [interupted? (client/interrupt ses)]
          (oprint :system ses "")
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
      (interrupt-all-sessions)
      (when (client/session?)
        ;(client/interrupt)
        ;(Thread/sleep 300)
        (client/session-close!))
      (let [id (client/session-create!)]
        (oprintln :system "New default session" id)))))


(defn restart-server []
  (interrupt-all-sessions)
  (server/stop!)
  (server/serve! 0)
  (oprintln :system "New server on port" (server/port))
  (recreate-session))


(defn output-root []
  (let [codearea
        (doto (ca/new-codearea false)
          (.setStyle "-fx-font-size:14;")
          (.setEditable false)
          (.setFocusTraversable true)
          (.setParagraphGraphicFactory (gutter-factory)))
        clear-button
        (fx/button
          "Clear"
          :onaction #(do (ca/set-text codearea "") 
                         (swap! gutter-types assoc :prevs [] :curr nil)
                         (.setParagraphGraphicFactory codearea (gutter-factory)))
          :tooltip "Clear output")

        top
        (layout/menubar true
          (doto clear-button (.setFocusTraversable false))
          (fx/region :hgrow :always)
          (layout/menu
            [:button "nREPL" :bottom
             [
              [:item "Ping all sessions" ping-sessions]
              [:item "Interrupt all sessions!" interrupt-all-sessions]
              [:separator]
              [:item "Create new default session" recreate-session]
              [:separator]
              [:item "Start new server" restart-server]]]))

        root
        (fx/borderpane
           :top top
           :center (VirtualizedScrollPane. codearea))]
    (.fire clear-button)
    (setup-output codearea)
    [root clear-button]))
