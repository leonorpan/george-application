;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.core.core
  (:require
    [clojure.pprint :refer [pprint pp] :as cpp]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]

    [george.code.core :as gcode]
    [clojure.string :as s]
    [george.core.history :as hist]
    [clojure.tools.nrepl :as nrepl]
    [clj-stacktrace.repl :refer [pst pst-str]]
    [clj-stacktrace.core :refer [parse-exception]]
    [george.application.repl :as repl]
    [george.application.repl-server :as repl-server]
    [george.util :as gu]
    [clj-stacktrace.utils :as utils]
    [clojure.string :as cs])


  (:import (javafx.geometry Pos)
           (java.io StringWriter PrintStream OutputStreamWriter StringReader)
           (org.apache.commons.io.output WriterOutputStream)
           (javafx.scene.text Text)
           (clojure.lang LineNumberingPushbackReader ExceptionInfo)

           (javafx.scene.input KeyEvent)
           (org.fxmisc.richtext StyledTextArea)
           (java.util.function BiConsumer)
           (java.util Collections Random)
           (javafx.scene.layout Pane GridPane Priority)
           (javafx.scene.control Alert$AlertType Alert)))


(defonce standard-out System/out)
(defonce standard-err System/err)
(declare output)




;;;; Input section ;;;;


;; from Versions.java in george-client
(def IS_MAC  (-> (System/getProperty "os.name") .toLowerCase (.contains "mac")))
(def SHORTCUT_KEY (if IS_MAC "CMD" "CTRL"))


(defn- ensure-newline [obj]
  "ensures that the txt ends with a new-line"
  (let [txt (if (nil? obj) "nil" (str obj))]
    (if (= "\n" (last txt))
      txt
      (str txt \newline))))



(declare read-eval-print-in-ns)


(defn- do-run [code-area repl-uuid current-history-index-atom ns-textfield clear? eval-button interrupt-button source-file]
    (let [input (gcode/text code-area)
          update-ns-fn #(fx/later (.setText ns-textfield %))
          eval-id (gu/uuid)]
        (if (s/blank? input)
            ;; then
            (println)
            ;; else
            (fx/later  ;; GUI interatactions must be on a JavaFX render thread
              (.setDisable eval-button true)

              (doto interrupt-button
                (.setDisable  false)
                (.setOnAction
                  (fx/event-handler
                    ;(println "Interrupting:" eval-id)
                    (repl/eval-interrupt eval-id)
                    (output :system "Interrupted!\n"))))

              (fxj/daemon-thread
                  (try
                    (read-eval-print-in-ns
                      input
                      (.getText ns-textfield)
                      eval-id
                      source-file
                      update-ns-fn)


                    ;; handle history and clearing
                    (hist/append-history repl-uuid input)
                    (reset! current-history-index-atom -1)
                    (when clear? (fx/later (.clear code-area)))

                    (catch Exception e
                      (.printStackTrace e))
                    (finally
                      ;; No matter what, I need to be able to eval again
                      (fx/later
                        (.setDisable interrupt-button true)
                        (.setDisable eval-button false)
                        (-> code-area .getScene .getWindow .requestFocus)))))))))



(defn- input-scene [ns source-file]
    (let [
          repl-uuid (gu/uuid)

          current-history-index-atom (atom -1)

          ns-label
          (doto
              (fx/label (or ns "user"))
              ( .setStyle "
                    -fx-font: 12 'Source Code Pro Regular';
                    -fx-text-fill: gray;"))

          code-area
          (doto (gcode/->codearea))

          do-history-fn
          (fn [direction global?]
              (hist/do-history code-area repl-uuid current-history-index-atom direction global?))

          clear-checkbox
          (fx/checkbox "Clear on 'Eval'"
            :tooltip "If selected, code is cleared when 'Eval' is  triggered (button or keyboard shortcut).")

          interrupt-button
          (doto
            (fx/button "X"
                       :width 30
                       :tooltip "Interrupt current 'Eval'")
            (.setDisable true))


          run-button
          (fx/button
            "Eval"
            :width 130
            :tooltip (format
                       "Run code, then clear if checkbox ckecked.          %s-ENTER
    Run code, then do the inverse of checkbox selection.   SHIFT-%s-ENTER" SHORTCUT_KEY SHORTCUT_KEY))


          do-run-fn
          (fn [inverse-clear]  ;; do the oposite of clear-checkbox
              (let [clear-checked
                    (.isSelected clear-checkbox)
                    do-clear
                    (if inverse-clear (not clear-checked) clear-checked)]
                (do-run code-area repl-uuid current-history-index-atom ns-label do-clear run-button interrupt-button source-file)))

          prev-button
          (doto
              (fx/button
                  (str  \u25B2)  ;; up: \u25B2,  left: \u25C0
                  :onaction #(do-history-fn hist/PREV false)
                  :tooltip (format
                               "Previous 'local' history.          %s-LEFT
Previous 'global' history.   SHIFT-%s-LEFT" SHORTCUT_KEY SHORTCUT_KEY)))


          next-button
          (doto
              (fx/button
                  (str \u25BC)  ;; down: \u25BC,  right: \u25B6
                  :onaction #(do-history-fn hist/NEXT false)
                  :tooltip (format
                               "Next 'local' history.          %s-RIGHT
Next 'global' history.   SHIFT-%s-RIGHT" SHORTCUT_KEY SHORTCUT_KEY)))

          button-box
          (fx/hbox
              prev-button
              next-button
              (fx/region :hgrow :always)
              clear-checkbox
              (fx/region :hgrow :always)
              interrupt-button
              run-button
              :spacing 3
              :alignment fx/Pos_TOP_RIGHT
              :insets [5 0 0 0])

          border-pane
          (fx/borderpane
              :center code-area
              :top ns-label
              :bottom button-box
              :insets 10)

          scene
          (doto
              (fx/scene border-pane :size [500 200])
              (fx/add-stylesheets "styles/codearea.css"))

          key-pressed-handler
          (fx/key-pressed-handler{
                                  #{:SHORTCUT :UP} #(do-history-fn hist/PREV false)
                                  #{:SHIFT :SHORTCUT :UP} #(do-history-fn hist/PREV true)

                                  #{:SHORTCUT :DOWN} #(do-history-fn hist/NEXT false)
                                  #{:SHIFT :SHORTCUT :DOWN} #(do-history-fn hist/NEXT true)

                                  #{:SHORTCUT :ENTER} #(do-run-fn false)
                                  #{:SHIFT :SHORTCUT :ENTER} #(do-run-fn true)})]

        (.setOnAction run-button (fx/event-handler (do-run-fn false)))

        (.addEventFilter border-pane KeyEvent/KEY_PRESSED key-pressed-handler)
        ;; TODO: ensure code-area alsways gets focus back when focus in window ...
        ;; TODO: colorcode also when history is the same
        ;; TODO: nicer tooltips.  (monospace and better colors)

        scene))


(defonce ^:private output-singleton (atom nil))


;; For Input stage layout
(defonce ^:private input-vertical-offset (atom 0))
(defonce ^:private input-horizontal-offset (atom 0))
(defn next-input-vertical-offset [] (swap! input-vertical-offset inc))
(defn next-input-horizontal-offset [] (swap! input-horizontal-offset inc))


(defn new-input-stage [& [ns]]
    ;; TODO: consolidate/fix integrations/dependencies
    ;; TODO: add interupt-posibility (button) to/for run-thread

    (fxj/thread (repl/session-ensured! true))
    (let [
          repl-nr
          (hist/next-repl-nr)

          scene (input-scene ns (str \" "Input " repl-nr \"))

          screen-WH (-> (fx/primary-screen) .getVisualBounds fx/WH)

          horizontal-offset (* (next-input-horizontal-offset) 5)
          vertical-offset (* (next-input-vertical-offset) 20)

          stage
          (fx/now
            (doto (fx/stage
                    :title (format "Input %s" repl-nr)
                    :scene scene
                    :sizetoscene true
                    :location [(- (first screen-WH) (.getWidth scene) 30 horizontal-offset)
                               (+ 80 vertical-offset)])))]

        stage))


(defn- normalize-cause-location [[F L C] source-file]
  [(if (#{(str \" source-file \") "NO_SOURCE_FILE" "null"} F)
     source-file
     F)
   (if (#{"0" nil} L) nil L)
   (if (#{"0" nil} C) nil C)])




(defn- find-cause-location
  "Attempts to locate file:line:column from printout.
   Returns a vector, else nil."
  [ex-str source-file]
  (let [compiling-info (re-find #"compiling:\((.+):(\d+):(\d+)\)" ex-str)
        no-source-file (re-find #"(NO_SOURCE_FILE):(\d+)" ex-str)]
    (cond
      compiling-info
      (normalize-cause-location
        (subvec compiling-info 1)
        source-file)
      no-source-file
      (normalize-cause-location
        (conj (subvec no-source-file 1) nil)
        source-file)
      :default
      nil)))





(defn- exception-dialog [header message details]
  ;; http://code.makery.ch/blog/javafx-dialogs-official/
  (let [textarea
        (doto (fx/textarea :text details :font (fx/SourceCodePro "Regular" 12))
          (.setEditable false)
          (.setWrapText false)
          (.setMaxWidth Double/MAX_VALUE)
          (.setMaxHeight Double/MAX_VALUE)
          (GridPane/setVgrow Priority/ALWAYS)
          (GridPane/setHgrow Priority/ALWAYS))
        label
        (fx/label "The exception stacktrace is:")
        ex-content
        (doto (GridPane.)
          (.setMaxWidth Double/MAX_VALUE)
          (.setPrefWidth 800)
          (.add label 0 0)
          (.add textarea 0 1))]

    (doto (Alert. Alert$AlertType/ERROR)
      (.setTitle "An error has occoured")
      (.setHeaderText header)
      (.setContentText message)
      (-> .getDialogPane (.setExpandableContent ex-content))
      (.showAndWait))))


(defn- update-type-in-parsed [orig]
  ;(println "/update-orig orig:" orig)
  (try (resolve orig)
       (catch Throwable t (.printStackTrace t))))


(defn- process-error [res source-file]
  ;(println "/process-error res:" res)
  (binding [*out* *err*]
    (let [parsed-ex
          (->
            (repl/eval-do
              :code "(clj-stacktrace.core/parse-exception *e)"
              :session (repl/session-get!))
            first
            :value
            read-string
            (update-in [:class] update-type-in-parsed)
            ((fn [m]
              (if (-> m :cause :class)
                  (update-in m [:cause :class] update-type-in-parsed)
                  m))))
          ex-str (pst-str parsed-ex)
          cause-location (find-cause-location ex-str source-file)]
      (println ex-str)
      (apply printf
             (cons "                file:  %s
                line:  %s
                row:   %s\n"
                cause-location))

      (when-not @output-singleton
        (fx/now
          (exception-dialog
            (-> parsed-ex :class .getName)

            (format "%s

    file:    %s
    line:   %s
    col:    %s"
                    (:message parsed-ex)
                    (first cause-location)
                    (if-let [r (second cause-location)] r "unknown")
                    (if-let [c (last cause-location)] c "unknown"))

            ex-str))))
    (println)))



(defn- process-response
  "Returns current-ns. Processes/prints results based on type."
  [res current-ns update-ns-fn]
  ;(pprint res)
  (let [ns (if-let [a-ns (:ns res)] a-ns current-ns)]

    (when (not= ns current-ns)
      (output :ns (ensure-newline (str " ns> " ns)))
      (update-ns-fn ns))

    (when-let [s (:value res)]
      (output :res (ensure-newline (str " >>> " s))))

    (when-let [st (:status res)]

      (output :system (ensure-newline  (cs/join " " st))))

    (when-let [o (:out res)]
      (print o) (flush))

    ns))


(defn- indent-input-lines-rest [s]
  (cs/replace s "\n" "\n ... "))


(defn read-eval-print-in-ns
  "returns nil"
  [^String code ^String ns eval-id ^String source-file update-ns-fn]
  (output :in (ensure-newline (str " <<< " (indent-input-lines-rest code))))
  (repl/def-eval
    {:code code
     :ns ns
     :session (repl/session-ensured! true)
     :id eval-id
     :line 1
     :column 1
     :file source-file}
    (loop [responses response-seq
           current-ns ns]
      (when-let [response (first responses)]
        (if (= "eval-error" (-> response :status first))
          (do
            (repl/eval-interrupt (:session response) eval-id)
            (process-error response source-file))
          (let [new-ns (process-response response current-ns update-ns-fn)]
            (recur (rest responses) new-ns)))))))


;;;; output section ;;;;


(defn- get-outputarea []
    (when-let [stage @output-singleton]
        (->  stage .getScene .getRoot .getChildrenUnmodifiable first)))


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



(defn output [typ obj]  ;; type is one of :in :ns :res :out :err :system
    ;; TODO: maybe crop old lines from beginning for speed?
    (if-let[oa (get-outputarea)]
        (fx/later
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


(defn- output-scene []
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

          scene
          (fx/scene (fx/borderpane
                        :top button-box
                        :center outputarea
                        :insets 5))]
        scene))


(defn- output-stage []
    (let [bounds (.getVisualBounds (fx/primary-screen))
          size [1000 300]]

        (fx/now
            (fx/stage
                :title "Output"
                :location [(+ (.getMinX bounds) 20)
                           (- (-> bounds .getMaxY (- (second size))) 20)]
                :size size
                :sizetoscene false
                :scene (output-scene)))))




(defn show-or-create-output-stage []
    (if @output-singleton
        (fx/later (.toFront @output-singleton))
        (do (wrap-outs)
            (reset! output-singleton
                    (fx/setoncloserequest
                        (output-stage)
                        #(do
                            (println "reset!-ing @output-singleton to nil")
                            (reset! output-singleton nil)
                            (unwrap-outs)))))))





