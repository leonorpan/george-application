;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  labs.graph
  "An edge/node graph example based on   http://stackoverflow.com/questions/30679025/graph-visualisation-like-yfiles-in-javafx "
    (:require [george.javafx :as fx]
              [clojure.pprint :refer [pprint]]
              [george.javafx.java :as j]
              [environ.core :refer [env]])
    (:import (javafx.scene.control ScrollPane)
             (javafx.scene.layout Pane)
             (javafx.scene.shape Circle)
             (javafx.scene.paint Color)
             (java.util Random)
             (javafx.scene.transform Scale)
             (javafx.scene.input ScrollEvent)))



(defn- create-zoomable-scrollpane [content-node]
  (let [
        delta 0.1  ;; scroll by this much

        transform-group (fx/group content-node) ;; gets transformed by the group
        layout-bounds-group  (fx/group transform-group)  ;; will resize to adapt to transform-group  zooms
        scrollpane (ScrollPane. layout-bounds-group) ;; will adjust scrollbars et al when layout-bounds-group expands
        scale-transform (Scale.)

        zoom-handler
        (fx/event-handler-2
            [_ scroll-event]
            (when (.isControlDown scroll-event)
                (let [scale-value (. scale-transform getX) ;; equal scaling in both directions
                      delta-y (. scroll-event getDeltaY)  ;; positive or negative
                      new-scale-value (+ scale-value (* delta (if (pos? delta-y) 1 -1)))]

                    (.setX scale-transform new-scale-value)
                    (.setY scale-transform  new-scale-value)

                    (. scroll-event consume))))]
        ;; Tips on keeping zoom centered: https://community.oracle.com/thread/2541811?tstart=0

      (-> transform-group .getTransforms (.add scale-transform))
      ;; eventfilter allows my code to get in front of ScrollPane's event-handler.
      (.addEventFilter scrollpane ScrollEvent/ANY zoom-handler)

    [scale-transform scrollpane]))




(defn- make-draggable [graph vertex]
    (let [
          point-atom (atom [0 0])
          scale (:scale graph)

          press-handler
          (fx/event-handler-2
              [_ event]
              (let [bounds (. (:node vertex) getBoundsInParent)
                    scale-value (. scale getX)]
                  (reset! point-atom [
                                      (- (* (. bounds getMinX) scale-value)
                                         (. event getScreenX))
                                      (- (* (. bounds getMinY) scale-value)
                                         (. event getScreenY))])))


          drag-handler
          (fx/event-handler-2
              [_ event]
              (let [
                    offset-x (+ (. event getScreenX) (first @point-atom))
                    offset-y (+ (. event getScreenY) (second @point-atom))
                    scale-value (. scale getX)]

                  (. (:node vertex) relocate
                     (/ offset-x scale-value)
                     (/ offset-y scale-value))))]


        (doto (:node vertex)
            (. setOnMousePressed press-handler)
            (. setOnMouseDragged drag-handler))

        vertex))



(defn- half-width-height [vertex]
  [(-> vertex :node .getBoundsInParent .getWidth (/ 2))
   (-> vertex :node .getBoundsInParent .getHeight (/ 2))])


(defn create-edge [source-vertex target-vertex]
  (let [
        line (fx/line :color Color/LIGHTBLUE)
        source-hwh (half-width-height source-vertex)
        target-hwh (half-width-height source-vertex)]

    (-> line .startXProperty (.bind (-> source-vertex :node .layoutXProperty (.add (first source-hwh)))))
    (-> line .startYProperty (.bind (-> source-vertex :node .layoutYProperty (.add (second source-hwh)))))
    (-> line .endXProperty (.bind (-> target-vertex :node .layoutXProperty (.add (first target-hwh)))))
    (-> line .endYProperty (.bind (-> target-vertex :node .layoutYProperty (.add (second target-hwh)))))

    {:a :EDGE :node line}))


(defn create-vertex [id]
    {:a :VERTEX
     :id   id
     :node (doto (Circle. 20. 20. 20.) (.setFill fx/WHITESMOKE) (.setStroke Color/BLUE) (.setStrokeWidth 2))})



(defn create-graph-panes []

  (let [
        vertexlayer (Pane.)
        canvas (fx/group vertexlayer)
        [scale scrollpane]
        (create-zoomable-scrollpane canvas)]

    {:scrollpane scrollpane
     :vertexlayer vertexlayer
     :scale scale}))


;;;; the graph model ;;;;

;; the model is based on this post:  http://stackoverflow.com/a/10242730/189280
;; and improved based on this post:  https://groups.google.com/d/msg/clojure/h1m6Qjuh3wA/pRqNY5HlYJEJ

;; We use the term "vertex" to refer to the element in the graph data structure,
;; and "node" to refer to a JavaFX Node.

(defn create-graph [] {})

(defn lookup-by-id
    "lookup a vertex based on its id. Returns the first match, else nil.  (Linear time)"
    [graph-or-childmap id]
    (first (filter #(= (:id %) id) (keys graph-or-childmap))))


(defn add-vertex [graph vertex]
    (if (graph vertex)
        graph
        (assoc graph vertex {})))


(defn add-edge [graph vertex child-vertex edge-object]
    (-> graph
        (add-vertex vertex)
        (add-vertex child-vertex)
        (update-in [vertex] assoc child-vertex edge-object)))


(defn- populate [graph graph-panes]
    (let [
          child-lookup-list ;; [id id] - for looking up child-vertices
          (map (fn [[c1 c2]] [ (str c1) (str c2)])  '("AB" "AC" "BC" "CD" "BE" "DF" "DG"))

          lookup-child-ids-fn ;; for getting list of child-ids for a given parent-id
          (fn [id]  (filter  #(= (first %) id)  child-lookup-list))

          graph ;; inserting new vertices without children
          (into graph (map
                          #(vector  (make-draggable  graph-panes (create-vertex (str %)))  {})
                          "ABCDEFG"))

          create-edges-fn
          (fn [graph vertex]
              (into {} (map
                           #(let [child-vertex (lookup-by-id graph (second %))]
                               [child-vertex (create-edge vertex child-vertex)])
                           (lookup-child-ids-fn (:id vertex)))))

          graph ;; now insert all children/edges
          (reduce
              (fn[graph [vertex _]]
                 (assoc graph vertex (create-edges-fn graph vertex)))
              graph graph)

          vertexlayer (:vertexlayer graph-panes)]


        (doseq [[vertex child-vertices] graph]
            (fx/add vertexlayer (:node vertex))
            (doseq [[_ edge] child-vertices]
                (fx/add vertexlayer (:node edge))
                (.toBack (:node edge))))
        graph))


(def ^:private randomizer (Random.))

(defn- randomize-layout [model]
         (doseq [c (map :node (keys model))]
             (let [x (* (. randomizer nextDouble) 400)
                   y (* (. randomizer nextDouble) 400)]
                 (. c relocate x y))))



(defn -main [& _]
  (let [
        graph-atom (atom (create-graph))
        graph-panes (create-graph-panes)]

    (swap! graph-atom populate graph-panes)
    (randomize-layout @graph-atom)
    (fx/later (fx/stage
                :title "graph"
                :location [100 50]
                :size [800 600]
                :scene (fx/scene (fx/borderpane :center (:scrollpane graph-panes) :insets 0))))))




;;;; DEV ;;;;

;(when (env :repl?) (println "WARNING: Running george.sandbox.graph/-main") (-main))
