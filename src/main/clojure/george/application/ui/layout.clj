;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.ui.layout
  (:require
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.application.ui.styled :as styled])
  (:import
    [javafx.scene Node]
    (javafx.scene.control TabPane)
    (javafx.scene.layout AnchorPane)))



(defn- nil-or-node? [n] (or (nil? n) (instance? Node n)))


(defn master-detail
  "Returns 3-item vector: [layout-root set-master-fn set-detail-fn]"
  [& [vertical?]]
  (let [detail-pane
        (fx/borderpane)

        root
        (fx/borderpane
          :center detail-pane)
        master-setter
        #(when (nil-or-node? %)
               (if vertical?
                   (.setTop root %)
                   (.setLeft root %)))
        detail-setter
        #(when (nil-or-node? %)
               (.setCenter detail-pane %))]
    [root master-setter detail-setter]))




(defn tabpane
  [empty-label newbutton-tooltip tab-factory start-with-one?]
  (let [
        t-pane
        (TabPane.)

        newbutton
        (fx/button "+" :tooltip newbutton-tooltip
                   :onaction
                   #(let [new-tab (tab-factory)]
                      (doto t-pane
                        (-> .getTabs (.add new-tab))
                        (-> .getSelectionModel (.select new-tab)))))

        newbutton-box
        (fx/hbox newbutton)

        empty-text
        (styled/heading empty-label)

        a-pane
        (doto (AnchorPane. (fxj/vargs-t Node empty-text t-pane newbutton-box))
              (.setMinHeight  2))]

    (doto empty-text
      (AnchorPane/setTopAnchor  8.0)
      (AnchorPane/setLeftAnchor 16.0))

    (doto t-pane
      (AnchorPane/setTopAnchor 0.0)
      (AnchorPane/setRightAnchor 40.0)
      (AnchorPane/setLeftAnchor 0.0)
      (AnchorPane/setBottomAnchor 0.0))

    (doto newbutton-box
      (AnchorPane/setTopAnchor 3.0)
      (AnchorPane/setRightAnchor 5.0))


    (when start-with-one?
      (.fire newbutton))

    a-pane))
