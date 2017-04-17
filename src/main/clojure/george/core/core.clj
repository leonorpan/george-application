;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
    ^{:author "Terje Dahl"}
    george.core.core
    (:require
        [clojure.pprint :refer [pprint pp]]
        [george.javafx :as fx]

        [george.javafx.java :as fxj]

        [george.code.core :as gcode]
        [clojure.string :as s]
        [george.core.history :as hist])

    (:import (javafx.geometry Pos)
             (java.io StringWriter PrintStream OutputStreamWriter StringReader)
             (org.apache.commons.io.output WriterOutputStream)
             (javafx.scene.text Text)
             (clojure.lang LineNumberingPushbackReader ExceptionInfo)

             (javafx.scene.input  KeyEvent)
             (org.fxmisc.richtext StyledTextArea)
             (java.util.function BiConsumer)
             (java.util Collections)))


(defonce standard-out System/out)
(defonce standard-err System/err)
(declare output)


(defonce ^:private input-vertical-offset (atom 0))

(defonce ^:private input-horizontal-offset (atom 0))

(defn next-input-vertical-offset [] (swap! input-vertical-offset inc))

(defn next-input-horizontal-offset [] (swap! input-horizontal-offset inc))


;;;; input section ;;;;


;; from Versions.java in george-client
(def IS_MAC  (-> (System/getProperty "os.name") .toLowerCase (.contains "mac")))
(def SHORTCUT_KEY (if IS_MAC "CMD" "CTRL"))
(def ^:const N \newline)


(defn- post-eval
    "Processes input and result, possibly updating RT-state and/or history.
    Intended used for updating namespace-instepctor etc."
    [source read-res eval-res current-ns prev-ns]
    (when (var? eval-res)
        (alter-meta! eval-res #(assoc % :source source))

        (doseq [token (if (seq? read-res) read-res [read-res])]
            (println "  " token (type token)))))



(defn- read-span [[after-l after-c] [before-l before-c] code]
    ;(output nil (str " after: " after-l ":" after-c " before: " before-l ":" before-c N))
    (let [rdr (LineNumberingPushbackReader. (StringReader. code))
          sb (StringBuilder.)]
        ;; step rdr forward to start-line and start-char
        (dotimes [_  (dec after-l)] (.readLine rdr))
        (dotimes [_  (dec after-c)] (.read rdr))

        (loop []
            ;; keep appending and looping until end-line and end-char are reached
            (when
                (or
                    (< (.getLineNumber rdr) before-l)
                    (and (= (.getLineNumber rdr) before-l)
                         (< (.getColumnNumber rdr) before-c)))
                (.append sb (char (.read rdr)))

                (recur)))
        ;; returned a trimmed string of read code
        (.trim (str sb))))



(defn- with-newline [obj]
    "ensures that the txt ends with a new-line"
    (let [txt (if (nil? obj) "nil" (str obj))]
        (if (s/ends-with? txt "\n")
            txt
            (str txt "\n"))))


(defn- print-error

    ([^ExceptionInfo ei]
     (let [{:keys [after before source]} (ex-data ei)]
         (print-error after before source
                      (. ei getMessage) (. ei getCause) ei)))

    ([after before source msg cause exception]
     (let [
           [after-l after-c] after
           [before-l before-c] before
           exception-trace (seq (.getStackTrace exception))
           supressed-trace (seq  (.getSuppressed exception))
           cause-trace (when-let [cause (.getCause exception)](seq (.getStackTrace cause)))
           total-error
           (format
            "# EXCEPTION!
%s
## SOURCE:
%s
## SOURCE SPAN:
from inclusive [%s:%s] to exclusive [%s:%s]  ([line:char])
## STACKTRACE EXCEPTION:
%s
## STACKTRACE SUPRESSED:
%s
## STACKTRACE CAUSE:
%s
"
            msg
            source
            after-l after-c before-l before-c
            exception-trace
            supressed-trace
            cause-trace)]



         (output :err total-error)
;         (.println standard-err loc-str)
         (.printStackTrace (or cause exception) standard-err))))




;; TODO:
"
The following does not give a useful printout - either from any repl or from a loaded file.
Solve this to make something more user-friendly: A more usable and beginner-firendly system!
(defn fail [v] (/ 2 v))
(fail 0)
"
(defn- output-ns [ns]
    (output :ns (str "namespace: " ns "\n")))


(defn- read-eval-print [code]
    (println)
    (output-ns *ns*)

    (output :in (with-newline code))
    (let [rdr (LineNumberingPushbackReader. (StringReader. code))]

        ;; inspiration:
        ;;   https://github.com/pallet/ritz/blob/develop/nrepl-middleware/src/ritz/nrepl/middleware/tracking_eval.clj
        ;;   https://github.com/pallet/ritz/blob/develop/repl-utils/src/ritz/repl_utils/compile.clj
        (push-thread-bindings
            {Compiler/LINE_BEFORE (Integer. (int 0))
             Compiler/LINE_AFTER (Integer. (int 0))})
        (try

            (loop [ns *ns*]
                (let [
                      after [(.getLineNumber rdr ) (.getColumnNumber rdr)]
                      _ (.set  Compiler/LINE_BEFORE (Integer. (first after)))

                      read-res
                      (try
                          (read rdr false :eof)
                          ;; pass exception into form for later handling,
                          ;; so we first can get the code end-point.
                          (catch Exception e e))

                      before [(.getLineNumber rdr) (.getColumnNumber rdr)]
                      _ (.set Compiler/LINE_BEFORE (Integer. (first before)))

                      source (read-span after before code)]

                    (when (instance? Exception read-res)
                        (throw
                            (ex-info
                                (.getMessage read-res)
                                {:after after :before before :source source}
                                (.getCause read-res))))

                    (when-not (= read-res :eof)
                            (try
                                (let [eval-res (eval read-res)]

                                    (output :res (with-newline eval-res))
                                    ;; we made it this far without a read-exception or eval-exception

                                    ;(output nil (str "read-res: " read-res \newline))

                                    #_(cond
                                        (not= *ns* ns)
                                        (do
                                            (output-ns *ns*)
                                            (output nil (str "   new ns: " *ns* " \n"))
                                            (output nil (str "   source: " source \newline)))


                                        (var? eval-res)
                                        (do
                                            (output nil (str "      var: " eval-res "   (RT-state store)\n"))
                                            (output nil (str "   source: " source \newline)))


                                        :default
                                        (do
                                            (output nil (str " call res: " eval-res "   (history store)\n"))
                                            (output nil (str "   source: " source \newline)))))

        ;                            (post-eval source read-res eval-res  *ns* ns)



                                ;; Catch and re-throw the eval-exception,
                                ;; adding start and end-point in code
                                (catch Exception e
                                    (throw
                                        (ex-info
                                            (.getMessage e)
                                            {:after after :before before :source source}
                                            (.getCause e)))))
                                ;; We only recur if no read or eval exception was thrown.
                            (recur *ns*))))
            ;; here we actually catch read and eval exceptions, and print them.
            ;; This is where something much more useful should be done!!
            (catch ExceptionInfo ei
                (print-error ei))

            (finally
                (pop-thread-bindings)))

        (str *ns*)))


(declare read-eval-print-in-ns)


(defn- do-run [code-area repl-uuid current-history-index-atom ns-textfield clear? eval-button]
    (let [input (gcode/text code-area)]
        (if (s/blank? input)
            (println)
            (fx/later  ;; GUI interatactions must be on a JavaFX render thread
                (.setDisable eval-button true)
                (fxj/daemon-thread
                    ;; From within the JavaFX thread we can spin of a new Java thread.
                    ;; But from within the Java-thread we must again place GUI interactions on
                    ;; the JavaFX render thread using `fx/later`.
                    (let [new-ns (read-eval-print-in-ns input (.getText ns-textfield))]
                        (when (not= new-ns (.getText ns-textfield))
                            (fx/later (.setText ns-textfield new-ns))
                            (output-ns (str "-> " new-ns)))
                        ;; handle history and clearing
                        (hist/append-history repl-uuid input)
                        (reset! current-history-index-atom -1)
                        (when clear? (fx/later (.clear code-area)))
                        ;; all state changes done, it is now safe to eval again
                        (fx/later
                          (.setDisable eval-button false)
                          (-> code-area .getScene .getWindow .requestFocus))))))))


(defn- input-scene [ns]
    (let [

          repl-uuid (hist/uuid)

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
                (do-run code-area repl-uuid current-history-index-atom ns-label do-clear run-button)))

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


(defn new-input-stage [& [ns]]
    ;; TODO: consolidate/fix integrations/dependencies
    ;; TODO: add interupt-posibility (button) to/for run-thread

  (let [
        repl-nr
        (hist/next-repl-nr)

        scene (input-scene ns)

        screen-WH (-> (fx/primary-screen) .getVisualBounds fx/WH)

        horizontal-offset (* (next-input-horizontal-offset) 5)
        vertical-offset (* (next-input-vertical-offset) 20)

        stage
        (fx/now
          (doto (fx/stage
                  :title (format "Input %s" repl-nr)
                  :scene scene
                  :sizetoscene true
                  :location [ (- (first screen-WH) (.getWidth scene) 30 horizontal-offset)
                             (+ 80  vertical-offset)])))]



       stage))


;;;; API ;;;;


(defn read-eval-print-in-ns
    "returns new ns as string"
    [^String code ^String ns]
    ;; useful?  Found in clojure.main/eval-opt
    ;    (let [cl (.getContextClassLoader (Thread/currentThread))]
    ;        (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl)))
    (binding [*ns* (create-ns (symbol ns)) *file* "'input'"]
        (read-eval-print code)))



(defonce ^:private output-singleton (atom nil))


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
          :err (->OutputStyleSpec "RED")
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
    (when (#{:in :res} typ)
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





