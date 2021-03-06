;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.ui.layout
  (:require
    [markdown.core :refer [md-to-html-string]]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.application.ui.styled :as styled]
    [clojure.string :as cs])
  (:import
    [javafx.scene Node]
    [javafx.scene.control TabPane SeparatorMenuItem MenuItem MenuButton Tab]
    [javafx.scene.layout AnchorPane]
    [javafx.scene.input KeyEvent]))



(defn- nil-or-node? [n] (or (nil? n) (instance? Node n)))


(defn master-detail
  "Returns 3-item vector: [layout-root set-master-fn set-detail-fn]"
  [& [vertical?]]
  (let [detail-pane
        (fx/borderpane)
        root
        (fx/borderpane :center detail-pane)
        master-setter
        #(when (nil-or-node? %)
               (if vertical? (.setTop root %) (.setLeft root %)))
        detail-setter
        #(when (nil-or-node? %) (.setCenter detail-pane %))]
    [root master-setter detail-setter]))


(defn- new-tab-action [tab-factory tpane]
  (let [^Tab tab (tab-factory)
        ocrh (.getOnCloseRequest tab)
        hcrh1
        (fx/event-handler-2
          [_ e]
          (when ocrh
            (.handle ocrh e))
          (when-not (.isConsumed e)
            (-> e .getSource  .getTabPane .getSelectionModel .selectNext)))]

    (.setOnCloseRequest tab hcrh1)

    (doto tpane
      (-> .getTabs (.add tab))
      (-> .getSelectionModel (.select tab)))))


(defn close-tab-nicely [tpane tab]
  (let [tpane (or tpane (.getTabPane tab))]
    (when-let [be (-> tpane .getSkin .getBehavior)]
      (let [tab (or tab (-> tpane .getSelectionModel .getSelectedItem))]
        (when (and tab (.canCloseTab be tab))  (.closeTab be tab))))))


(defn tabpane
  [empty-label newbutton-tooltip tab-factory start-with-one?]
  (let [
        tpane
        (doto (TabPane.)
              (-> .getStyleClass (.add TabPane/STYLE_CLASS_FLOATING)))

        newbutton
        (doto (fx/button "+" :tooltip  newbutton-tooltip
                             :onaction #(new-tab-action tab-factory tpane))
              (.setFocusTraversable false))

        newbutton-box
        (fx/hbox newbutton)

        empty-text
        (styled/new-heading empty-label)

        root
        (doto (AnchorPane. (fxj/vargs-t Node empty-text tpane newbutton-box))
              (.setMinHeight  2))]

    (doto empty-text
      (AnchorPane/setTopAnchor  8.0)
      (AnchorPane/setLeftAnchor 16.0))

    (doto tpane
      (AnchorPane/setTopAnchor 0.0)
      (AnchorPane/setRightAnchor 40.0)
      (AnchorPane/setLeftAnchor 0.0)
      (AnchorPane/setBottomAnchor 0.0))

    (doto newbutton-box
      (AnchorPane/setTopAnchor 3.0)
      (AnchorPane/setRightAnchor 5.0))

    (when start-with-one?
      (.fire newbutton))

    (.addEventFilter tpane
                      KeyEvent/KEY_PRESSED
                      (fx/key-pressed-handler {#{:N :SHORTCUT} #(.fire newbutton)}))
                                               ;#{:C :SHORTCUT}  #(close-tab-nicely tpane nil)}))

    [root tpane]))


(defn set-listeners [tabpane selected_ focused_]

  (when selected_
    (-> tabpane
        .getSelectionModel
        .selectedIndexProperty
        (.addListener
          (fx/changelistener [_ _ ip i]
                             (reset! selected_
                                     (when-not (neg? i)
                                       (-> tabpane .getTabs (.get i)))))))

    (when focused_
      (-> tabpane
        .focusedProperty
        (.addListener
          (fx/changelistener [_ _ _ b]
                             (reset! focused_ b)))))

    tabpane))


(defn menu
  "[:button label side children]
  [:separator]
  [:item label action]"
  [root]
  (let [typ (first root)]
    (condp = typ
      :separator (SeparatorMenuItem.)
      :item      (let [[_ label action] root] (doto (MenuItem. label) (fx/set-onaction action)))
      :button    (let [[_ label side children] root]
                   (doto (MenuButton. label  nil (fxj/vargs* (map menu children)))
                         (.setStyle "-fx-box-border: -fx-text-box-border;")
                         (.setPopupSide (fx/side side))
                         (.setFocusTraversable false))))))


(defn menubar [top? & items]
  (doto
    (apply fx/hbox
           (concat items
                   [:spacing 3
                    :insets (if top? [0 0 5 0] [5 0 0 0])
                    :padding 5
                    :alignment fx/Pos_TOP_LEFT]))
                    ;:background fx/GREEN]))
    (.setBorder (styled/new-border (if top? [0 0 1 0] [1 0 0 0])))))


(defn- code-tag [text state]
  [(cs/replace text #"<code>" "<code class=\"clj\">") state])


(defn- ahref-tag [text state]
  [(cs/replace text #"<a href" "<a <a onclick=\"window.status='CLICK:'+this;return false;\" href") state])


(defn nonspaced-br [text {:keys [code lists] :as state}]
  ;(prn "  ## text:" text)
  [(if (and (not (or code 
                     lists
                     (cs/ends-with? text ">")
                     (empty? text))))
     (str text "<br />")
     text)
   state])

(defn doc->html
  "Returns the markdown as an html-string"
  [^String md]
  (let [html (md-to-html-string md :custom-transformers [code-tag ahref-tag nonspaced-br])]
    ;(println html)
    html))