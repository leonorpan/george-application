(ns george.application.output-input
  (:require
    [george.application [output :as output]
     [input :as input]]
    [george.util.singleton :as singleton]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.core.history :as hist]
    [george.application.launcher :as launcher])
  (:import (javafx.scene.control SplitPane Tab TabPane)
           (javafx.geometry Orientation)
           (javafx.scene Node)
           (javafx.scene.layout AnchorPane)))


(defn- input-tab []
  (let [nr (hist/next-repl-nr)]
    (doto (Tab.
               (format "Input %s " nr)
               (first (input/input-root "user.turtle" (format "\"Input %s\"" nr)))))))


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

(def xy [(+ (launcher/xyxy 2) 5) 95])

(def wh [800 (- (launcher/xyxy 3) (xy 1))])


(defn- create-stage []
  (fx/now
    (let [oi-root (output-input-root)

          stage
          (fx/stage
            :title " Input-Output - a.k.a. REPL (Read Eval Print Loop)"
            :location xy
            :size wh
            :sizetoscene false
            :scene (doto (fx/scene oi-root)
                     (fx/add-stylesheets "styles/codearea.css"))

            :onhidden #(do (output/teardown-output)
                           (singleton/remove OIS_KW)))

          ;; Calculate the exact position for divider
          in-h 200
          h (wh 1)
          out-h (- h in-h)
          div-pos (/ out-h h)]
      (.setDividerPosition oi-root 0 div-pos) ;; must be called after stage is created
      stage)))


(defn show-or-create-stage []
  ;(println "oi/show-or-create-stage")
  (doto
    (singleton/get-or-create OIS_KW create-stage)
    (.toFront)))


