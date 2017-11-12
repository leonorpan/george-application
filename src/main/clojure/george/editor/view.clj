;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.editor.view
  (:require
    [george.javafx.java :as fxj]
    [george.javafx :as fx]
    [george.editor.state :as st]
    [george.util :as u])

  (:import (org.fxmisc.flowless Cell VirtualFlow)
           (javafx.scene.text Text)
           (javafx.scene.layout Region StackPane Pane)
           (javafx.geometry Pos Insets BoundingBox)
           (javafx.scene Node Group)
           (javafx.scene.paint Color)
           (javafx.scene.shape Ellipse Rectangle)
           (javafx.scene.control Label)))


;(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(def DEFAULT_FONT_SIZE 16)
(def DEFAULT_FONT (fx/SourceCodePro "medium" DEFAULT_FONT_SIZE))

(def DEFAULT_LINE_HEIGHT 28.0)
(def DEFAULT_LINE_INSETS (fx/insets 0.0, 24.0, 0.0, 12.0))

(def DEFAULT_TAB_WIDTH (/ ^int DEFAULT_FONT_SIZE 2.0))

(def DEFAULT_TEXT_COLOR fx/ANTHRECITE)
(def DEFAULT_TEXT_SELECTION_COLOR (fx/web-color "#b3d8fd"))

(def DEFAULT_HIDDEN_CHAR_COLOR Color/GRAY)



(def DEFAULT_LINE_BACKGROUND_COLOR fx/WHITESMOKE)
(def DEFAULT_CURRENT_LINE_BACKGROUND_COLOR (fx/web-color "#F0F0FF"))
(def DEFAULT_CURRENT_LINE_BORDER_COLOR (fx/web-color "#E5E5FF"))


(def DEFAULT_GUTTER_INSETS (fx/insets 0.0, 14.0, 0.0, 14.0))
(def DEFAULT_GUTTER_TEXT_FILL (fx/web-color "#999"))
(def DEFAULT_GUTTER_FONT (fx/SourceCodePro "medium" 14))
(def DEFAULT_GUTTER_BACKGROUND (fx/color-background DEFAULT_LINE_BACKGROUND_COLOR));(fx/web-color "#ddd")))
(def DEFAULT_GUTTER_BORDER (fx/make-border DEFAULT_CURRENT_LINE_BORDER_COLOR [0 1 0 0]))


(defn- ^Node selection-background-factory [^double w ^double h c]
  (let [rect (fx/rectangle :size [(inc w) h] :fill DEFAULT_TEXT_SELECTION_COLOR)]
    (cond
      (= c \newline)
      (doto
        ^StackPane
        (fx/stackpane
           (doto (Ellipse. w (/ h 2))
             (.setFill DEFAULT_TEXT_SELECTION_COLOR))
           rect)
        (.setAlignment Pos/CENTER_LEFT))

      :default
      rect)))


(defn- ^Rectangle anchor-factory [height]
  (let [rect (fx/rectangle :size [0.5 height] :fill (Color/DODGERBLUE))]
    rect))


(defn- cursor-factory [height]
  (let [rect (fx/rectangle :size [3 height ] :fill (Color/DODGERBLUE))]
    rect))


(def DEFAULT_CURSOR_FACTORY cursor-factory)


(definterface IRowCell
  ^int (getColumn [^double offset-x])
  ^double (getOffsetX [^int col]) ;; returns "absolute" offset - compensating for scrolling
  ^double (getGutterWidth [])
  ^int (getIndex []))

(definterface IScrollableText
  ^int (getColumn [^double offset-x])
  ^double (getOffsetX [^int col]))  ;; returns offset relative to itself.


(definterface IGutter
  ^double (getWidth [])
  (setNumber [n])
  (dispose []))


(defn- ^String nr-formatter
  "Returns a formatted string padded with zeros based on max-n"
  [max-n n]
  (let [digits (if (= max-n 0) 1 (inc (int (Math/log10 max-n))))]
    (format (str "%0" digits "d") (inc ^int n))))
;(println (nr-formatter 3574 5))
;(println (nr-formatter 3574 115))


(defn- new-paragraph-gutter
  "AKA (left) margin, graphic, number-row.
  Let's use this as a general data-carrier for the line"
  [line-count_]

  (let [
        k (Object.)
        n_ (atom 0)

        nr-label
        (doto ^Label (fx/label (nr-formatter @line-count_ @n_))
          (.setFont DEFAULT_GUTTER_FONT)
          (.setBackground DEFAULT_GUTTER_BACKGROUND)
          (.setPrefHeight (+ 2.0 ^double DEFAULT_LINE_HEIGHT))
          (.setTextFill DEFAULT_GUTTER_TEXT_FILL)
          (.setPadding DEFAULT_GUTTER_INSETS)
          (.setBorder DEFAULT_GUTTER_BORDER))
        node
        (proxy [Group IGutter] [(fxj/vargs nr-label)]
          (getWidth []
            (.getWidth nr-label))

          (setNumber [n]
            (reset! n_ n)
            (.setText nr-label (nr-formatter @line-count_ @n_)))

          (dispose []
            (remove-watch line-count_ k)))]
    (add-watch line-count_ k
               (fn [_ _ _ cnt]
                   (.setText nr-label (nr-formatter cnt @n_))))

    node))


(defn- new-text [char]
  (cond
    (= char \newline)
    (doto (Text. (str " " \u21A9))  ;\u23CE
      (.setFill DEFAULT_HIDDEN_CHAR_COLOR))

    (= char \tab)
    (doto (Text. (str \u21E5))
      (.setWrappingWidth DEFAULT_TAB_WIDTH)
      (.setFill DEFAULT_HIDDEN_CHAR_COLOR))

    :default
    (doto (Text. (str char))
      (.setFont DEFAULT_FONT)
      (.setFill DEFAULT_TEXT_COLOR))))


(defn- insert-and-layout-chars-as-texts
  "Inserts chars into Text-nodes.
  Returns [total-width texts] ;; [double vector-of-Text]"
  [^StackPane pane chars]
  (let [texts (mapv new-text chars)
        _ (->  pane .getChildren (.setAll (fxj/vargs-t* Text texts)))

        width
        (loop [x 0.0 i 0 nodes texts chars chars]
          (if-let [n ^Text (first nodes)]
            (do
              (.setTranslateX n x)
              (let [w (-> n .getBoundsInParent .getWidth)]
                (recur (+ x w) (inc i) (next nodes) (next chars))))
            x))]
    [width texts]))


(defn- calculate-offset
  "Returns the offset-x of where the mark (anchor/caret) should be inserted."
  [texts col]
  (if (zero? ^int col)
    0.0
    (let [t ^Text (get texts (dec ^int col))]
      (-> t .getBoundsInParent .getMaxX))))


(defn- set-markings
  "Inserts and lays out markings (caret, anchor, select) if any, on the passed-in pane."
  [^StackPane pane state-derived row chars texts]
  (->  pane .getChildren .clear)
  (let [
        {:keys [caret anchor caret-pos anchor-pos state]} state-derived
        [crow ccol] caret-pos
        [arow acol] anchor-pos

        caret-row? (= crow row)
        anchor-row? (= arow row)

        [low ^int high] (sort [caret anchor])
        do-mark? (partial u/in-range? low (dec high))

        ^int row-index (st/location->index_ state [row 0])]

    (loop [x 0.0 i 0 nodes texts chars chars]
      (when-let [n ^Text (first nodes)]
        (let [w (-> n .getBoundsInParent .getWidth)]
          (when (do-mark? (+ row-index i))
            (let [marking (selection-background-factory w DEFAULT_LINE_HEIGHT (first chars))]
              (.setTranslateX ^Node marking (- x 0.5)) ;; offset half pixel to left
              (-> pane .getChildren (.add marking))))
          (recur (+ x w) (inc i) (next nodes) (next chars)))))

    (when anchor-row?
      (let [anchor (anchor-factory DEFAULT_LINE_HEIGHT)]
        (.setTranslateX anchor (- ^double (calculate-offset texts acol) 0.25))
        (-> pane .getChildren (.add anchor))))

    (when caret-row?
      (let [caret ^Node (DEFAULT_CURSOR_FACTORY DEFAULT_LINE_HEIGHT)]
        (.setTranslateX caret (- ^double (calculate-offset texts ccol) 1.0)) ;; negative offset for cursor width
        (-> pane .getChildren (.add caret))))))


(defn- set-markings-maybe
  "If the row is in the set, then delegates the task"
  [^StackPane pane state-derived row chars texts]
  (when ((:update-marking-rows state-derived) row)
    (set-markings ^StackPane pane state-derived row chars texts)))


(defn- calculate-col [^double offset-x char-nodes]
  ;(println "view/calculate-col offset-x:" offset-x)
  (if (neg? offset-x)
    0
    (loop [x 0.0 col 0  nodes char-nodes]
      ;(println "  ## col:" col)
      (if-let [n ^Text (first nodes)]
        (let  [w (-> n .getBoundsInParent .getWidth)
               xw (+ x w)]
          (if (<= x offset-x xw) ;; Do we have a hit?
            (if (> offset-x (+ x (/ w 2))) ;; Round up if right of center of node
              (inc col)
              col)
            (recur
                xw
                (inc col)
                (next nodes))))
        ;; Ran out of nodes.  Just return what we have.
        col))))


(defn ensure-caret-visible [^VirtualFlow flow derived]
  ;(println "ensure-caret-visible")
  (let [[^long row col] (:caret-pos derived)
        cell (.getCell flow row)
        ^double offset-x (.getOffsetX ^IRowCell cell col)
        ^double gutter-w (.getGutterWidth ^IRowCell cell)
        ^double scrolled-x (-> flow .breadthOffsetProperty .getValue)
        flow-w (.getWidth flow)
        main-w (- flow-w gutter-w)
        visible-padding 8.0
        col-visible? (< (+ gutter-w visible-padding) offset-x (- flow-w visible-padding))
        bounding-x (- (+ offset-x scrolled-x) gutter-w (/ main-w 3))
        bounding-w main-w
        bounding-box (BoundingBox. bounding-x 0 bounding-w DEFAULT_LINE_HEIGHT)

        visible-cells (.visibleCells flow)
        ^int first-visible-row (.getIndex ^IRowCell (first visible-cells))
        ^int last-visible-row (.getIndex ^IRowCell (last visible-cells))]

    (when-not col-visible?
      (.show flow row bounding-box))
    (when (<= row first-visible-row)
      (.show flow (dec row)))
    (when (>= row last-visible-row)
      (.show flow (inc row)))))


(defn- highlight-current-line [^StackPane pane state-derived row]
  (let [crow (-> state-derived :caret-pos first)
        caret-row? (= crow row)]

    (if caret-row?
      (doto pane
        (.setBorder (fx/make-border  DEFAULT_CURRENT_LINE_BORDER_COLOR [1 0 1 0]))
        (.setBackground (fx/color-background DEFAULT_CURRENT_LINE_BACKGROUND_COLOR)))
      (doto pane
        (.setBorder (fx/make-border  DEFAULT_LINE_BACKGROUND_COLOR [1 0 1 0]))
        (.setBackground (fx/color-background DEFAULT_LINE_BACKGROUND_COLOR))))))


(defn- new-scrolling-part [texts-pane marks-pane gutter graphic-offset_ texts texts-width]
  (let [
        insets ^Insets DEFAULT_LINE_INSETS
        inset-left (.getLeft  insets)
        inset-right (.getRight insets)

        scrolling-pane
        (doto
          (proxy [StackPane IScrollableText] [(fxj/vargs marks-pane texts-pane)]
            ;; Impelements IScrollableText
            (getColumn [^double offset-x] ;; offset-x already considers scrolled offset
              (let [^double  gw (.getWidth gutter)
                    offset (- offset-x gw inset-left)
                    col
                    (if (<  (- offset-x ^double @graphic-offset_) gw) ;; offset-x is in/under in gutter.
                      :gutter
                      (calculate-col offset texts))]
                col))
            ;; Impelements IScrollableText
            (getOffsetX [col]
                (+ ^double (calculate-offset texts col) inset-left)))

          (.setAlignment Pos/CENTER_LEFT)
          (.setPrefHeight DEFAULT_LINE_HEIGHT)
          (.setPrefWidth (+ inset-left ^double texts-width inset-right))
          (.setPadding insets))]

    scrolling-pane))


(defn new-paragraph-cell [state-derived_ graphic-offset_ line-count_ line-data]
  (let [k (Object.)

        row_ (atom -1)

        chars (seq line-data)

        line-background-pane
        (Pane.)

        gutter
        (new-paragraph-gutter line-count_)

        texts-pane
        (doto ^StackPane (fx/stackpane)
          (.setAlignment Pos/CENTER_LEFT))

        [texts-width texts]
        (insert-and-layout-chars-as-texts texts-pane chars)

        marks-pane
        (doto ^StackPane (fx/stackpane)
          (.setAlignment Pos/CENTER_LEFT))

        scrolling-part
        (new-scrolling-part texts-pane marks-pane gutter graphic-offset_ texts texts-width)

        node
        (proxy [Region] []
          ;; @override
          (computeMinWidth [^double _]
            (.computePrefWidth this -1.0))
          ;; @override
          (computePrefWidth [^double _]
            (.layout this)
            (let [insets  ^Insets (.getInsets ^Region this)]
              (+ ^double (.getWidth  gutter)
                 (.prefWidth ^Region scrolling-part -1.0)
                 (.getLeft  insets)
                 (.getRight insets))))
          ;; @override
          (computePrefHeight [^double _]
            (.layout ^Region this)
            DEFAULT_LINE_HEIGHT)
          ;; @override
          (layoutChildren []
            (let [[^double w h] (-> ^Region this .getLayoutBounds fx/WH)
                  gw ^double (.getWidth gutter)
                  go @graphic-offset_]

              (.resizeRelocate ^StackPane scrolling-part gw 0 (- w gw) h)
              ;; maybe check if 'gutter' is present (maybe nil?)
              (.resizeRelocate ^Region gutter go 0 gw h)
              (.resizeRelocate  line-background-pane go 0 w h))))]


    (-> node .getChildren (.setAll  (fxj/vargs-t Node
                                                 line-background-pane
                                                 scrolling-part
                                                 gutter)))

    (add-watch graphic-offset_ k (fn [_ _ _ _]
                                   (.requestLayout node)))
    (add-watch state-derived_ k (fn [_ _ _ derived]
                                  (set-markings-maybe marks-pane derived @row_ chars texts)
                                  (highlight-current-line line-background-pane derived @row_)
                                  (.requestLayout node)))

    (reify
      Cell
      ;; implements
      (getNode [_]
        node)
      ;; implements
      (updateIndex [_ index]
        ;(println "  ## updateIndex index:" index)
        (when (not= @row_ index) ;; only update box if index changes
          (reset! row_ index)
          (.setNumber gutter index)
          (set-markings-maybe marks-pane @state-derived_ @row_ chars texts)
          (highlight-current-line line-background-pane @state-derived_  @row_)
          (.requestLayout node)))
      ;; implements
      (dispose [_]
        (remove-watch graphic-offset_ k)
        (remove-watch state-derived_ k)
        (.dispose gutter))
      IRowCell
      ;; implements
      (getColumn [_ offset-x]
        (.getColumn scrolling-part offset-x))
      (getOffsetX [_ col]
        (-
         (+ ^double (.getWidth gutter)
            ^double (.getOffsetX scrolling-part col))
         ^double @graphic-offset_))
      (getGutterWidth [_]
        (.getWidth gutter))
      (getIndex [_]
        @row_))))


