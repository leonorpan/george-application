;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns labs.game1
    (:require
        [clojure.repl :refer [doc]]
        [george.javafx.java :as j]
        [george.javafx :as fx]
        [environ.core :refer [env]])
    (:import [javafx.scene Node Scene]
             [javafx.scene.shape Rectangle]
             [javafx.scene.layout StackPane Pane]
             [javafx.scene.paint Color]
             [javafx.scene.text Text]))




;(defn clicked [node f]
;    (. node setOnMouseClicked (fx/event-handler (f))))





(defn ->tile [^Node content loc-x loc-y]
    (let [
             rectangle
             (doto (Rectangle. 48 48 Color/LIGHTSTEELBLUE))

            pane
             (doto (StackPane. (j/vargs-t Node rectangle content))
                 (.setBorder (fx/new-border Color/TRANSPARENT 2.))
                 (. setStyle "-fx-background-color: cornflowerblue;")
                 (. relocate loc-x loc-y))]

        pane))


(defn ->swirl [loc-x loc-y]
    (let [
            tile
            (doto (->tile (Text. "swirl") loc-x loc-y)
                (. setOnMouseClicked
                    (fx/event-handler-2 [_ event]
                        (. (. event getSource) setVisible false))))]

        tile))


(defn- scene []
    (let [
             pane
             (doto (Pane.)
                 (. setStyle "-fx-background-color: #eee;")
                 (. setPrefSize 200 200))

             swirl1
             (->swirl 100 80)

             swirl2
             (->swirl 200 120)]


        (-> pane .getChildren (. addAll (j/vargs swirl1 swirl2)))
        (Scene. pane 800 600)))





(defn -main [& args]
    (fx/later
        (doto (fx/stage)
            (. setTitle "DB test")
            (. sizeToScene)
            (. setScene (scene))
            (. centerOnScreen)
            (. toFront)
            (. show))))


;(when (env :repl?) (println "WARNING: Running dev.labs.db.test/-main" (-main)))
