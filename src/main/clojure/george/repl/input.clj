(ns george.repl.input

    (:require
        [clojure.repl :refer [doc]]
        [clojure.string :as s]

        [george.javafx.java :as j] :reload
        [george.javafx.core :as fx] :reload
        [george.output :as output] :reload
        [george.repl.history :as hist] :reload
        [george.code.highlight :as dah] :reload
        [george.code.core :as gcode] :reload)

    (:import
        [java.io StringReader]
        [clojure.lang Compiler LineNumberingPushbackReader ExceptionInfo]

        [javafx.scene Node]
        [javafx.geometry Pos]
        [javafx.scene.input KeyEvent KeyCode]
        [javafx.stage Screen]))




;; from Versions.java in george-client
(def IS_MAC  (-> (System/getProperty "os.name") .toLowerCase (.contains "mac")))
(def SHORTCUT_KEY (if IS_MAC "CMD" "CTRL"))



(defn- with-newline [obj]
    "ensures that the txt ends with a new-line"
    (let [txt (if (nil? obj) "nil" (str obj))]
        (if (s/ends-with? txt "\n")
            txt
            (str txt "\n"))))


(defn- print-error

    ([^ExceptionInfo ei]
     (let [{:keys [after before]} (ex-data ei)]
        (print-error after before (. ei getMessage) (. ei getCause) ei)))

    ([after before msg cause exception]
     (let [loc-str (str "     [line column] - starting at " after ", ending by " before ": " \newline)]
        (output/output :err  loc-str)
        (.println output/standard-err  loc-str)
        (.printStackTrace (or cause exception)))))



(defn- read-eval-print [code]
    (println)
    (output/output :in (with-newline code))
    (let [rdr (LineNumberingPushbackReader. (StringReader. code))]

      ;; inspiration:
      ;;   https://github.com/pallet/ritz/blob/develop/nrepl-middleware/src/ritz/nrepl/middleware/tracking_eval.clj
      ;;   https://github.com/pallet/ritz/blob/develop/repl-utils/src/ritz/repl_utils/compile.clj
       (push-thread-bindings
           {Compiler/LINE_BEFORE (Integer. (int 0))
            Compiler/LINE_AFTER (Integer. (int 0))})
       (try

          (loop []
              (let [
                       after [(.getLineNumber rdr ) (.getColumnNumber rdr)]
                       _ (.. Compiler/LINE_BEFORE (set (Integer. (first after))))

                       form
                       (try
                            (read rdr false :eof)
                           ;; pass exception into form for later handling
                            (catch Exception e e))

                      before [(.getLineNumber rdr ) (.getColumnNumber rdr)]
                      _ (.. Compiler/LINE_BEFORE (set (Integer. (first before))))]

                   (if (instance? Exception form)
                       (print-error after before (.getMessage form ) (.getCause form ) form)

                       (when-not (= form :eof)
                           (when-not (instance? Exception form)
                               (try
                                   (output/output :res (with-newline (eval form)))
                                   (catch Exception e
                                       (throw
                                           (ex-info
                                               (. e getMessage)
                                               {:after after :before before}
                                               (. e getCause)))))
                               (recur))))))
          (catch ExceptionInfo ei
              (print-error ei))

          (finally
            (pop-thread-bindings)))


       (str *ns*)))


#_(defn- read-eval-print-in-ns
      "Evals expressions in str, prints each non-nil result using prn"
      [code-str ns-sym]
      ;; useful?
      ;    (let [cl (.getContextClassLoader (Thread/currentThread))]
      ;        (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl)))

      (binding [*ns* ns-sym]
          (let [eof (Object.)
                reader (LineNumberingPushbackReader. (StringReader. code-str))]
              (loop [input (read reader false eof)]
                  (when-not (= input eof)
                      (let [value (eval input)]
                          (when-not (nil? value)
                              (prn value))
                          (recur (read reader false eof))))))))


(defn read-eval-print-in-ns
    "returns new ns as string"
    [^String code ^String ns]
    (binding [*ns* (create-ns (symbol ns)) *file* "'input'"]
        (read-eval-print code)))


(defn- do-run [code-area repl-uuid current-history-index-atom ns-textfield clear?]
    (let [input (gcode/text code-area)]
        (if (s/blank? input)
            (println)
            (j/thread
                (let [new-ns (read-eval-print-in-ns input (.getText ns-textfield))]
                    (fx/thread (.setText ns-textfield new-ns))
                    (output/output :ns (with-newline new-ns))
                    ;; handle history and clearing
                    (hist/append-history repl-uuid input)
                    (reset! current-history-index-atom -1)
                    (when clear? (fx/later (.clear code-area))))))))




(defn- input-scene []
    (let [

          repl-uuid (hist/uuid)

          current-history-index-atom (atom -1)

          ns-label
             (doto
                 (fx/label "user")
                 ( .setStyle "
                    -fx-font: 12 'Source Code Pro Regular';
                    -fx-text-fill: gray;
                "))


             code-area
             (doto (gcode/->codearea)
                 #_( .setStyle "
                -fx-font: 14 'Source Code Pro Medium';
                -fx-padding: 5 5;
                /* -fx-border-radius: 4; */
                "))



          do-history-fn
          (fn [direction global?]
              (hist/do-history code-area repl-uuid current-history-index-atom direction global?))

          do-run-fn
          (fn [clear?]
              (do-run code-area repl-uuid current-history-index-atom ns-label clear?))

          prev-button
            (doto
                (fx/button
                    (str  \u25C0)  ;; up: \u25B2
                    :onaction #(do-history-fn hist/PREV false)
                    :tooltip (format
                               "Previous 'local' history.          %s-LEFT
Previous 'global' history.   SHIFT-%s-LEFT" SHORTCUT_KEY SHORTCUT_KEY)))
                ; (-> .getStyleClass (.add "default-button"))
                ;(.setId "repl-prev-button")



            next-button
            (doto
                (fx/button
                    (str \u25B6)  ;; down \u25BC
                    :onaction #(do-history-fn hist/NEXT false)
                    :tooltip (format
                               "Next 'local' history.          %s-RIGHT
Next 'global' history.   SHIFT-%s-RIGHT" SHORTCUT_KEY SHORTCUT_KEY)))
                ; (-> .getStyleClass (.add "default-button"))
                ;(.setId "repl-next-button")



            run-button
            (fx/button
                "Eval"
                :width 130
                :onaction #(do-run-fn false)
                :tooltip (format
                           "Run code, then clear.          %s-ENTER
Run code, don't clear.   SHIFT-%s-ENTER" SHORTCUT_KEY SHORTCUT_KEY))


            button-box
            (fx/hbox
                prev-button
                next-button
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
                                  #{:CTRL :LEFT} #(do-history-fn hist/PREV false)
                                  #{:SHIFT :CTRL :LEFT} #(do-history-fn hist/PREV true)

                                  #{:CTRL :RIGHT} #(do-history-fn hist/NEXT false)
                                  #{:SHIFT :CTRL :RIGHT} #(do-history-fn hist/NEXT true)

                                  #{:CTRL :ENTER} #(do-run-fn true)
                                  #{:SHIFT :CTRL :ENTER} #(do-run-fn false)})]



        (.addEventFilter border-pane KeyEvent/KEY_PRESSED key-pressed-handler)
        ;; TODO: ensure code-area alsways gets focus back when focus in window ...
        ;; TODO: colorcode also when history is the same
        ;; TODO: nicer tooltips.  (monospace and better colors)

        scene))




(defn new-input-stage []
    ;; TODO: consolidate/fix integrations/dependencies
    ;; TODO: accept namspace-arg, and/or return namespace-atom
    ;; TODO: add interupt-posibility (button) to/for run-thread

    (let [
          repl-nr
          (hist/next-repl-nr)

          stage
          (fx/now
              (doto (fx/stage
                        :title (format "Input %s" repl-nr)
                        :scene (input-scene)
                        :sizetoscene true)
                        ;(. centerOnScreen))

                  (.setX (-> (Screen/getPrimary) .getVisualBounds .getWidth (/ 2)))
                  (.setY (-> (Screen/getPrimary) .getVisualBounds .getHeight (/ 2) (- 300)))))]


        stage))




(defn -main
    "Launches an input-stage as a stand-alone app."
    [& args]
    (println "george.input-stage/-main")
    (fx/later (new-input-stage)))


;;; DEV ;;;

;(println "WARNING: Running george.input/-main" (-main))
