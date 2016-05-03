(ns
  george.sandbox.george.sandbox.graph
  "An edge/node graph example based on   http://stackoverflow.com/questions/30679025/graph-visualisation-like-yfiles-in-javafx "
  (:require [george.javafx :as fx])

    (:import (javafx.scene.control ScrollPane)
             (javafx.scene.layout Pane)
             (javafx.scene.shape Circle)
             (javafx.scene.paint Color)
             (java.util Random)
             (javafx.scene.transform Scale)))







(defn- create-zoomable-scrollpane [content-node]
  (let [

        delta 0.1

        zoom-group (fx/group content-node)
        content-group (fx/group zoom-group)
        scrollpane (ScrollPane. content-group)
        scale-transform (Scale.)

        zoom-to
        (fn [v]
            (. scale-transform (setX v))
            (. scale-transform (setY v)))

        zoom-handler
        (fx/event-handler-2
            [_ scroll-event]
            (let [scale-value (. scale-transform getX) ;; equal scaling in both directions
                  delta-y (. scroll-event getDeltaY)  ;; positive or negative
                  new-scale-value (+ scale-value (* delta (if (pos? delta-y) 1 -1)))
                  ]
                (zoom-to new-scale-value)
                (. scroll-event consume)))
        ]
      (doto zoom-group
          (-> .getTransforms (.add scale-transform))
          (.setOnScroll zoom-handler))

    [scale-transform scrollpane]))




(defn- make-draggable [graph cell]
    (let [
          point-atom (atom [0 0])

          scale (:scale graph)

          press-handler
          (fx/event-handler-2
              [_ event]
              (let [bounds (. (:node cell) getBoundsInParent)
                    scale-value (. scale getX)]
                  (reset! point-atom [
                                      (- (* (. bounds getMinX) scale-value)
                                         (. event getScreenX))
                                      (- (* (. bounds getMinY) scale-value)
                                         (. event getScreenY))
                                      ])))

          drag-handler
          (fx/event-handler-2
              [_ event]
              (let [
                    offset-x (+ (. event getScreenX) (first @point-atom))
                    offset-y (+ (. event getScreenY) (second @point-atom))
                    scale-value (. scale getX)
                    ]
                  (. (:node cell) relocate
                     (/ offset-x scale-value)
                     (/ offset-y scale-value))))
          ]

        (doto (:node cell)
            (. setOnMousePressed press-handler)
            (. setOnMouseDragged drag-handler)
            ;(. setOnMouseRelease release-handler)
            )
        cell))






(defn- half-width-height [cell]
  [(-> cell :node .getBoundsInParent .getWidth (/ 2))
   (-> cell :node .getBoundsInParent .getHeight (/ 2))])


(defn create-edge [source-cell target-cell]
  (let [
        line (fx/line :color Color/LIGHTBLUE)
        source-hwh (half-width-height source-cell)
        target-hwh (half-width-height source-cell)
        ]
    (-> line .startXProperty (.bind (-> source-cell :node .layoutXProperty (.add (first source-hwh)))))
    (-> line .startYProperty (.bind (-> source-cell :node .layoutYProperty (.add (second source-hwh)))))
    (-> line .endXProperty (.bind (-> target-cell :node .layoutXProperty (.add (first target-hwh)))))
    (-> line .endYProperty (.bind (-> target-cell :node .layoutYProperty (.add (second target-hwh)))))

    {:source source-cell :target target-cell :node line}))

#_(defn add-child-cell [cell child-cell]
  (update-in cell [:children] conj child-cell))

#_(defn add-parent-cell [cell parent-cell]
  (update-in cell [:parents] conj parent-cell))


(defn create-cell [id]
    {:id   id
     :node (doto (Circle. 20. 20. 20.)
             (.setFill fx/WHITESMOKE)
             (.setStroke Color/BLUE)
             (.setStrokeWidth 2)
             )
     :children []
     :parents []
     })

(defn create-graph []

  (let [
        celllayer (Pane.)
        canvas (fx/group celllayer)
        [scale scrollpane]
        (create-zoomable-scrollpane canvas)
        ]
    {:scrollpane scrollpane
     :celllayer celllayer
     :scale scale}))


(defn- create-model []
  {:cell-map {}})


(defn- populate [model graph]
  (let [
        cell-map (into {}
                       (map
                         (fn [c]
                           [(str c) (make-draggable  graph (create-cell (str c)))])
                         "ABCDEFG"))
        edges (map
                (fn [[c1 c2]]
                  (create-edge (cell-map (str c1)) (cell-map (str c2))))
                (list "AB" "AC" "BC" "CD" "BE" "DF" "DG"))
        celllayer (:celllayer graph)
        ]

    (doseq [e edges] (fx/add celllayer (:node e)))
    (doseq [c (vals cell-map)] (fx/add celllayer (:node c)))

    (assoc model :cell-map cell-map)
    ))

(def ^:private randomizer (Random.)
  )
(defn- randomize-layout [model]
  (doseq [c (map :node (vals (:cell-map model)))]
    (let [x (* (. randomizer nextDouble) 400)
          y (* (. randomizer nextDouble) 400)]
      (. c relocate x y)
      )))

(defn -main [& args]
  (let [
        model-atom (atom (create-model))
        graph (create-graph)
        ]
    (swap! model-atom populate graph)
    (randomize-layout @model-atom)
    (fx/later (fx/stage
                :title "graph"
                :location [100 50]
                :size [800 600]
                :scene (fx/scene (fx/borderpane :center (:scrollpane graph) :insets 0))
                ))
    ))


;;;; DEV ;;;;

(println "  ## WARNING: running `-main` from george.sandbox.graph") (-main)
