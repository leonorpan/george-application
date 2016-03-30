(ns george.input

    (:require
        [clojure.repl :refer [doc]]
        [clojure.string :as s]

        [george.java :as j] :reload
        [george.javafx :as fx] :reload
        [george.javafx-classes :as fxc] :reload
        [george.output :as output] :reload
        [dev.andante.highlight :as dah]
        :reload
        )
    (:import
        [java.io StringReader]
        [clojure.lang LineNumberingPushbackReader ExceptionInfo]

        )
    )


(fxc/import-classes)


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
            (print-error after before
                (. ei getMessage) (. ei getCause) ei)))

        ([after before msg cause exception]
            (let [loc-str (str "     [line column] - starting at " after ", ending by " before ": " \newline)]
                (output/output :err  loc-str)
                (. output/standard-err println loc-str)
                (. (or cause exception) printStackTrace)))
    )


(defn- read-eval-print [code]
    (output/output :in (with-newline code))
    (let [rdr (LineNumberingPushbackReader. (StringReader. code))]

            ;; inspiration:
            ;;   https://github.com/pallet/ritz/blob/develop/nrepl-middleware/src/ritz/nrepl/middleware/tracking_eval.clj
            ;;   https://github.com/pallet/ritz/blob/develop/repl-utils/src/ritz/repl_utils/compile.clj
             (push-thread-bindings
                 {clojure.lang.Compiler/LINE_BEFORE (Integer. (int 0))
                  clojure.lang.Compiler/LINE_AFTER (Integer. (int 0))})
             (try

                (loop []
                    (let [
                             after [(. rdr getLineNumber) (. rdr getColumnNumber)]
                             _ (.. clojure.lang.Compiler/LINE_BEFORE (set (Integer. (first after))))

                             form
                             (try
                                  (read rdr false :eof)
                                 ;; pass exception into form for later handdling
                                  (catch Exception e e))

                            before [(. rdr getLineNumber) (. rdr getColumnNumber)]
                            _ (.. clojure.lang.Compiler/LINE_BEFORE (set (Integer. (first before))))

                        ]

                        (if (instance? Exception form)
                            (print-error after before (. form getMessage) (. form getCause) form))

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
                                (recur)))))
                 (catch ExceptionInfo ei
                     (print-error ei))

                 (finally
                     (pop-thread-bindings)
                     ))

                 (str *ns*)))


(declare
    read-eval-print-in-ns)


(defn- run [code-area modified? ns-textfield]
    (let [input (dah/get-text code-area)]
        (if (s/blank? input)
            (println)
            (j/thread
                (let [new-ns (read-eval-print-in-ns input (. ns-textfield getText))]
                    (fx/thread (. ns-textfield setText new-ns))
                    (output/output :ns (with-newline new-ns))

                    ;; handle history and clearing

                    )))))



(defn- input-scene []
    (let [

             ns-label
             (doto
                 (Label. "user")
                 ( .setStyle "
                    -fx-font: 12 'Source Code Pro Regular';
                    -fx-text-fill: gray;
                "))


             code-area
             (doto (dah/codearea)
                 (BorderPane/setMargin (Insets. 5 0 0 0))
                 #_( .setStyle "
                -fx-font: 14 'Source Code Pro Medium';
                -fx-padding: 5 5;
                /* -fx-border-radius: 4; */
                ")
                 )



#_(comment
            prev-button
            (doto (jfx/button (str  \u25B2) #(history-button-fn -1 false)) ;; small: \u25B4
                (-> .getStyleClass (.add "default-button"))
                (.setId "repl-prev-button")
                (gui/install-tooltip (format "Previous 'local' history.   %s-UP\nAccess 'global' history by using SHIFT-%s-UP" SHORTCUT_KEY SHORTCUT_KEY))
                )

            next-button
            (doto (jfx/button (str \u25BC) #(history-button-fn 1 false)) ;; small: \u25BE
                (-> .getStyleClass (.add "default-button"))
                (.setId "repl-next-button")
                (gui/install-tooltip (format "Next 'local' history.   %s-DOWN\nAccess 'global' history by using SHIFT-%s-DOWN" SHORTCUT_KEY SHORTCUT_KEY))
                )
)
            run-button
            (fx/button
                "Eval"
                :width 130
                :onaction (run code-area false ns-label)
;                (gui/install-tooltip (format "Run code.   %s-ENTER\nPrevent clearing of code by using SHIFT-%s-ENTER" SHORTCUT_KEY SHORTCUT_KEY))
                :tooltip (format "Run code.  %s-ENTER" SHORTCUT_KEY)
                )

            button-box
            (doto
                (HBox. 3.0
                    (j/vargs-t Node
                        ;                                                  prev-button
                        ;                                                  next-button
                        (doto (Region.) (HBox/setHgrow Priority/ALWAYS))
                        run-button))
                (.setAlignment Pos/TOP_RIGHT)
                (BorderPane/setMargin (Insets. 5 0 0 0)))

            border-pane
             (doto
                 (BorderPane. code-area ns-label nil button-box nil)
                 (. setPadding (Insets. 10)))

            scene
             (doto
                 (Scene. border-pane 300 300)
                 (fx/add-stylesheets "styles/codearea.css")
                 )

            key-handler
            (fx/event-handler-2 [_ ke]
                (if (and (= (.getEventType ke) KeyEvent/KEY_PRESSED)
                        (.isShortcutDown ke))
                    (condp = (.getCode ke)
                        ; KeyCode/UP    (do (history-button-fn -1 (.isShiftDown ke)) (.consume ke))
                        ; KeyCode/DOWN  (do (history-button-fn 1 (.isShiftDown ke)) (.consume ke))
                        KeyCode/ENTER
                        (do (run code-area (.isShiftDown ke) ns-label) (.consume ke))
                        :default)))


             ]
        (. border-pane addEventFilter KeyEvent/KEY_PRESSED key-handler)
        ;; TODO: ensure code-area alsways gets focus back when focus in window ...

        scene ))



;;;; API ;;;;

(defn- read-eval-print-in-ns
    "Evals expressions in str, prints each non-nil result using prn"
    [code-str ns-sym]
      ;; useful?
      ;    (let [cl (.getContextClassLoader (Thread/currentThread))]
      ;        (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl)))

    (binding [*ns* ns-sym]
        (let [eof (Object.)
              reader (clojure.lang.LineNumberingPushbackReader. (java.io.StringReader. code-str))]
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


(defn new-input-stage []
    (let [
             scene
             (input-scene)
             stage
             (doto (Stage.)
                 (. setScene scene)
                 (. sizeToScene)
                 ;                      (. centerOnScreen)
                 (. setX (-> (Screen/getPrimary) .getVisualBounds .getWidth (/ 2) ))
                 (. setY (-> (Screen/getPrimary) .getVisualBounds .getHeight (/ 2) (- 300) ))
                 (. setTitle "Input")
                 (. show)
                 (. toFront))
             ]
        nil))






;;;; dev ;;;;

#_(defn -main
    "Launches an input-stage as a stand-alone app."
    [& args]
    (println "george.input-stage/-main")
    (fx/dont-exit!)
    (fx/thread (show-new-input-stage)))

;(-main)

;(run "(println \"(+ 2 3)\"))\n(+ 4 (+ 2 3))")
;(run "(println (+ 2 3)))\n((+ 4 5)")



nil