(ns george.output-stage
    (:import [javafx.collections ListChangeListener])

    (:require
        [clojure.repl :refer [doc]]
        [clojure.string :as s]

        [george.java :as j] :reload
        [george.javafx :as fx] :reload
        [george.javafx-classes :as fxc] :reload
        )

    )

(fx/init)
(fxc/import-classes)



(defn- output-scene-and-text-flow []
    (let [
            text-flow (TextFlow.)
            ;; TODO: beautify text a bit - monospace-font, size, padding ..
            scroll-pane (ScrollPane. text-flow)
            scene (Scene. (StackPane. (j/vargs scroll-pane)) 300 300)
         ]
        (-> text-flow
            .getChildren
            (. addListener
               (reify ListChangeListener
                    (onChanged [this change]
                        (. text-flow layout)
                        (doto scroll-pane (. layout) (. setVvalue 1.0))))))

        (def append (fn [node] (fx/thread (-> text-flow .getChildren (. add node)))))

        [scene text-flow]))



(declare close-output-stage)


(defn- output-stage-and-text-flow []
    (let [
             [scene text-flow]
                (output-scene-and-text-flow)
            stage
                (doto (Stage.)
                      (. setScene scene)
                      (. sizeToScene)
                      ;(.setX (-> (Screen/getPrimary) .getVisualBounds .getWidth (/ 2) (+ 200)))
                      (. centerOnScreen)
                      (. setTitle "Output")
                      (. show)
                      (. toFront)
                      (. setOnCloseRequest (fx/event-handler (close-output-stage))))
          ]
        {:stage stage :text-flow text-flow}))


(defonce ^{:private true} output-singleton (atom nil))

(defn- styled [typ ^Text text]
    (doto text
        (. setFill
            (condp = typ
                :in Color/BLUE
                :res Color/GREEN
                :err Color/RED
                Color/BLACK
        ))))


(defn output [typ txt]
    (when-let [{text-flow :text-flow} @output-singleton]
        (let [
;                 text (Text. (str "[" typ "] " txt))
                text (Text. (str txt))
                text (styled typ text)
             ]
            (fx/thread (-> text-flow .getChildren (. add text))))))


;;;; API ;;;;


;(defn out
;    "Prints the object(s) to output-stage, if it is shown (also if it is minimized or hidden)."
;    [& txts]
;    (apply output :out txts))
;
;
;(defn outln
;    "Same as 'out', followed by 'newline'."
;    [& txts]
;    (apply out txts)
;    (apply out "\n"))



(defn close-output-stage
    "Closes the output-stage if one has been created - whether it is visible or not."
    []
    (when-let [{stage :stage} @output-singleton]
        (fx/thread
            (. stage close))
            (reset! output-singleton nil)))


(defn output-stage-showing? []
    (boolean @output-singleton))


(defn show-output-stage
    "Shows the output-stage. Creates (a new) one if neccessary, de-minimzes and brings to front if necessary. Only one output-stage exists."
    []
    (fx/thread
        (if-let [{stage :stage} @output-singleton]
            (doto stage (. setIconified false) (. toFront))
            ;; else
            (reset! output-singleton (output-stage-and-text-flow)))))



(defn -main
    "Launches output-stage as a stand-alone app."
    [& args]
    (println "george.output-stage/-main")
    (fx/dont-exit!)
    (fx/thread (show-output-stage)))




;;;; dev ;;;;

(-main)