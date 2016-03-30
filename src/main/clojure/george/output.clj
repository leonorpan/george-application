(ns george.output

    (:require
        [clojure.repl :refer [doc]]
        [clojure.string :as s]

        [george.java :as j] :reload
        [george.javafx :as fx] :reload
        [george.javafx-classes :as fxc] :reload
        )
    (:import [javafx.collections ListChangeListener]

             [java.io StringWriter OutputStreamWriter PrintStream]
             [org.apache.commons.io.output WriterOutputStream]
             )
    )

(fx/init)
(fxc/import-classes)


(defonce standard-out System/out)
(defonce standard-err System/err)

(defonce ^{:private true} stage-singleton (atom nil))

(declare
    close-output-stage
    output
    )


(defn- output-string-writer [typ] ;; type is one of :out :err
    (proxy [StringWriter] []
        (flush []
            ;; first print the content of StringWriter to output-stage
            (let [s (str this)]
                (if (= typ :err)
                    (. standard-err print s)
                    (. standard-out print s))
                (output typ s))
            ;; then flush the buffer of the StringWriter
            (let [sb (. this getBuffer)]
                (. sb delete 0 (. sb length))))))


(defn wrap-outs []
    (let [
             ow (output-string-writer :out)
             ew (output-string-writer :err)
             ]
        (System/setOut (PrintStream. (WriterOutputStream. ow) true))
        (System/setErr (PrintStream. (WriterOutputStream. ew) true))
        (alter-var-root #'*out* (constantly ow))
        (alter-var-root #'*err* (constantly ew))
        ))

(wrap-outs)

(defn unwrap-outs []
    (System/setOut standard-out)
    (System/setErr standard-err)
    (alter-var-root #'*out* (constantly (OutputStreamWriter. System/out)))
    (alter-var-root #'*err* (constantly (OutputStreamWriter. System/err)))
    )



(defn- styled [typ ^Text text]
    (doto text
        (. setFill
            (condp = typ
                :in Color/BLUE
                :res Color/GREEN
                :ns Color/GRAY
                :err Color/RED
                Color/BLACK ;; default (:out)
                ))))


(defn- get-text-flow []
    (when-let [stage @stage-singleton]
        (->  stage .getScene .getRoot .getChildrenUnmodifiable first .getContent)
        ))



(defn- output-scene []
    (let [
             text-flow
             (doto
                 (TextFlow.)
                 (. setStyle "
                     -fx-font: 14 'Source Code Pro Regular';
                     -fx-padding: 5 5;

                 "))
             scroll-pane (ScrollPane. text-flow)
             scene (Scene. (StackPane. (j/vargs scroll-pane)) 600 300)
             ]
        (-> text-flow
            .getChildren
            (. addListener
                (reify ListChangeListener
                    (onChanged [this change]
                        (. text-flow layout)
                        (doto scroll-pane (. layout) (. setVvalue 1.0))))))

        (def append (fn [node] (fx/thread (-> text-flow .getChildren (. add node)))))

        scene ))





(defn- create-output-stage []
    (let [
             scene
             (output-scene)
             stage
             (doto (Stage.)
                 (. setScene scene)
                 (. sizeToScene)
                 (. setX 100)
                 (. setY (-> (Screen/getPrimary) .getVisualBounds .getHeight (/ 2) ))
                 (. setTitle "Output")
                 (. show)
                 (. toFront)
                 (. setOnCloseRequest (fx/event-handler (close-output-stage))))
             ]
        (wrap-outs)
        stage))



;;;; API ;;;;


(defn output [typ obj]  ;; type is one of :in :ns :res :out :err
    (if-let [tf (get-text-flow)]
        (fx/thread
            (-> tf .getChildren (. add (styled typ (Text. (str obj)))))))
            ;; TODO: crop old lines from beginning - for speed.
    ;; else - make sure these always also appear in stout
    (if (#{:in :res} typ)
        (. standard-out print (str obj))

        ))



(defn close-output-stage
    "Closes the output-stage if one has been created - whether it is visible or not."
    []
    ;(unwrap-outs)  ;; not necessary
    (println "closing output-stage ...")
    (when-let [stage @stage-singleton]
        (fx/thread
            (. stage close))
        (reset! stage-singleton nil)))




(defn show-output-stage
    "Shows the output-stage. Creates (a new) one if neccessary,
    de-minimzes and brings to front if necessary. Only one output-stage exists."
    []
    (fx/thread
        (if-let [stage @stage-singleton]
            (doto stage (. setIconified false) (. toFront))
            ;; else
            (reset! stage-singleton (create-output-stage)))))





;;;; dev ;;;;


(defn -main
    "Launches output-stage as a stand-alone app."
    [& args]
    (println "george.output-stage/-main")
    (fx/dont-exit!)
    (fx/thread (show-output-stage)))


;(-main)
;(println (text-flow))



nil