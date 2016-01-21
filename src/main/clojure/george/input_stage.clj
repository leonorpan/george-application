(ns george.input-stage

    (:require
        [clojure.repl :refer [doc]]
        [clojure.string :as s]

        [george.java :as j] :reload
        [george.javafx :as fx] :reload
        [george.javafx-classes :as fxc] :reload

        [george.input :as input] :reload

        )


    )

(fx/init)
(fxc/import-classes)





(defn- output-scene []
    (let [

             code-area
             (doto (TextArea.)
                 (.setStyle "
                -fx-font: 14 'Source Code Pro Medium';
                -fx-padding: 5 5;
                -fx-border-radius: 4;
                "))

             run-fn
                 (fn [clear?]
                     (let [input (-> code-area .getText)]
                         (if (s/blank? input)
                             (println)
                             (j/thread (input/run input)))))


            key-handler     (fx/event-handler-2 [_ ke]
                         (if (and (= (.getEventType ke) KeyEvent/KEY_PRESSED)
                                 (.isShortcutDown ke))
                             (condp = (.getCode ke)
                                 ; KeyCode/UP    (do (history-button-fn -1 (.isShiftDown ke)) (.consume ke))
                                 ; KeyCode/DOWN  (do (history-button-fn 1 (.isShiftDown ke)) (.consume ke))
                                 KeyCode/ENTER (do (run-fn (not (.isShiftDown ke))) (.consume ke))
                                 :default)))

             scene (Scene. (StackPane. (j/vargs code-area)) 300 300)
         ]
         (. code-area addEventFilter KeyEvent/KEY_PRESSED key-handler)

        scene ))




;;;; API ;;;;


(defn- show-new-input-stage []
    (let [
             scene
                (output-scene)
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




(defn -main
    "Launches an input-stage as a stand-alone app."
    [& args]
    (println "george.input-stage/-main")
    (fx/dont-exit!)
    (fx/thread (show-new-input-stage)))




;;;; dev ;;;;

(-main)