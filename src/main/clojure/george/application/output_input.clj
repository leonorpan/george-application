(ns george.application.output-input
  (:require
    [george.application [output :as output]
     [input :as input]]
    [george.util.singleton :as singleton]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.core.history :as hist])
  (:import (javafx.scene.control SplitPane Tab TabPane)
           (javafx.geometry Orientation)
           (javafx.scene Parent Node)
           (javafx.scene.layout Pane AnchorPane)
           (javafx.scene.paint Color)))


(defn- input-tab []
  (let [nr (hist/next-repl-nr)]
    (doto (Tab.
               (format "Input %s " nr)
               (input/input-root "user.turtle" (format "\"Input %s\"" nr))))))


(defn- input-root []
  (let [


        tab-pane
        (doto
          (TabPane. (fxj/vargs (input-tab))))


        button-box
        (fx/hbox
          (fx/button "+" :tooltip "New Input tab"
                     :onaction
                     #(let [new-tab (input-tab)]
                        (doto tab-pane
                          (-> .getTabs (.add new-tab))
                          (-> .getSelectionModel (.select new-tab))))))
        a-pane
        (doto
          (AnchorPane. (fxj/vargs-t Node tab-pane button-box)))]
       (AnchorPane/setTopAnchor button-box 3.0)
       (AnchorPane/setRightAnchor button-box 5.0)
       (AnchorPane/setTopAnchor tab-pane 0.0)
       (AnchorPane/setRightAnchor tab-pane 40.0)
       (AnchorPane/setLeftAnchor tab-pane 1.0)
       (AnchorPane/setBottomAnchor tab-pane 1.0)

    (doto a-pane
      (.setMinHeight  2))))


(defn output-input-root []
  (let [o-root (output/output-root)

        split-pane
        (doto
          (SplitPane.
            (fxj/vargs-t Node
                         o-root
                         (input-root)))

          (.setOrientation Orientation/VERTICAL))]

    split-pane))



(def OIS_KW ::output-input-stage)


(defn- create-stage []
  (fx/now
    (let [oi-root (output-input-root)
          bounds (.getVisualBounds (fx/primary-screen))
          size [1000 400]

          stage
          (fx/stage
            :title "Output & Input"
            :location [(.getMinX bounds)
                       (- (.getMaxY bounds)  (second size))]
            :size size
            :sizetoscene false
            :scene (doto (fx/scene oi-root)
                     (fx/add-stylesheets "styles/codearea.css"))

            :onhidden #(do (output/teardown-output)
                           (singleton/remove OIS_KW)))]

      (.setDividerPosition oi-root 0 1.) ;; must be called after stage is created

      stage)))


(defn show-or-create-stage []
  ;(println "oi/show-or-create-stage")
  (doto
    (singleton/get-or-create OIS_KW create-stage)
    (.toFront)))


