;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns dev.andante.dnd
    (:require
        [clojure.repl :refer [doc]]
        [george.javafx.java :as j]
        [george.javafx :as fx])
    (:import [javafx.scene Parent Group Node SnapshotParameters Cursor Scene]
             [javafx.scene.layout Pane StackPane FlowPane]
             [javafx.scene.text Text]
             [javafx.scene.input MouseEvent TransferMode ClipboardContent]
             [javafx.scene.paint Color]
             [javafx.geometry VPos]
             [javafx.stage StageStyle Screen Stage]))


(defn first-child [^Parent p]
(-> p .getChildren (.get 0)))


(defprotocol Code
    (toCode [this]) ; converts object to clojure code"
    )

(extend-protocol Code
    Group
    (toCode [this] (-> this first-child toCode))
    Pane
    (toCode [this] (-> this first-child toCode))
    Text
    (toCode [this] (-> this .getText))
    MouseEvent
    (toCode [this] (-> this .getSource toCode))
    Node
    (toCode [this] (-> this str))
    nil
    (toCode [_] "nil")

    )

(defn make-dropspot [spot]
    (.setOnDragOver spot (fx/event-handler-2 [_ event] ;; DragEvent
        (.acceptTransferModes event (j/vargs TransferMode/COPY))
        (.consume event)))

    (.setOnDragDropped spot (fx/event-handler-2 [_ event]
        (let [
                 board (.getDragboard event)
                 new-node (Text. (.getString board))
            ]
            (-> spot .getChildren (.add new-node))
            (.setDropCompleted event true)
            (.consume event))))
    spot)


(defn make-dnd [^Node node]
    (let [press-XY (atom nil)]

        (.setOnMousePressed node (fx/event-handler-2 [_ me] (reset! press-XY (fx/XY me))))

        (.setOnMouseDragged node (fx/event-handler-2 [_ me] (.consume me)))

        (.setOnDragDetected node (fx/event-handler-2 [_ me]
            (println "starting drag: " (toCode me))
            (let [
                     db (.startDragAndDrop node (j/vargs TransferMode/COPY TransferMode/MOVE))
                     cc (doto (ClipboardContent.) (.putString (toCode me)))
                     [x y] @press-XY
                     [w h] (fx/WH node)
                     hoff (- (/ w 2) x)
                     voff (- y (/ h 2))
                     params (doto (SnapshotParameters.) (.setFill Color/TRANSPARENT))
                     ]
                (println "[x y]:" [x y])
                (println "[w h]:" [w h])
                (println "hoff:" hoff)
                (println "voff:" voff)

                (.setCursor node Cursor/MOVE)
                (.setOpacity node 0.8)
                (.setDragView db (.snapshot node params nil) hoff voff)
                (.setOpacity node 0.2)

                (.setContent db cc)
                (.consume me))))

        (.setOnDragDone node (fx/event-handler-2 [_ me]
            (.setOpacity node 1.0)
            (.setCursor node Cursor/DEFAULT)
            (.consume me)))

        node))


(defn- canvas-scene []
    (let [
            pane
                (doto (FlowPane. 20. 20.)
                    (.setBorder (fx/make-border Color/TRANSPARENT 20.))
                    (make-dropspot))
        ]
        (Scene. pane 600 800)))




(defn- palette-scene []
    (let [
            text (doto
                     (Text. "drag me &\ndrop me")
                     (.setTextOrigin VPos/TOP)
                     (make-dnd)
                     )
             ]
        (Scene. (StackPane. (j/vargs text)) 200 400)))



(defn -main [& args]
    (println "dev.dnd/-main")
    (fx/later
        (doto (Stage. StageStyle/UTILITY)
            (.setScene (palette-scene))
            (.sizeToScene)
            (.setTitle "Palette")
            (.setX (-> (Screen/getPrimary) .getVisualBounds .getWidth (/ 2) (+ 200)))
            (.show))
        (doto (Stage.)
            (.setScene (canvas-scene))
            (.sizeToScene)
            (.centerOnScreen)
            (.setTitle "Canvas")

            (.show))

        ))



;(println "WARNING: Running dev.andante.dnd/-main") (-main)