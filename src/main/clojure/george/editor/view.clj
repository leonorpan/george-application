;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.editor.view
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.core.async :as a]
    [george.javafx.java :as fxj]
    [george.javafx :as fx]
    [george.editor.state :as st]
    [george.util :as u]
    [george.util.text :as ut])

  (:import (org.fxmisc.flowless Cell VirtualFlow)
           (javafx.scene.text Text Font FontPosture)
           (javafx.scene.layout Region StackPane Pane)
           (javafx.geometry Pos Insets BoundingBox)
           (javafx.scene Node Group)
           (javafx.scene.paint Color)
           (javafx.scene.shape Ellipse Rectangle)
           (javafx.scene.control Label)))


;(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(def ^:const DEFAULT_FONT_SIZE 16)
(def DEFAULT_FONT (fx/SourceCodePro "medium" DEFAULT_FONT_SIZE))

(def ^:const DEFAULT_LINE_HEIGHT 28.0)
(def DEFAULT_LINE_INSETS (fx/insets 0.0, 24.0, 0.0, 12.0))

(def DEFAULT_TAB_WIDTH (/ ^int DEFAULT_FONT_SIZE 2.0))

(def DEFAULT_TEXT_COLOR fx/ANTHRECITE)
(def DEFAULT_TEXT_SELECTION_COLOR (fx/web-color "#b3d8fd"))

(def DEFAULT_HIDDEN_CHAR_COLOR Color/DARKGRAY)

;; https://www.sessions.edu/color-calculator/
(def DEFAULT_BLOCK_COLOR (fx/web-color "#c5b3fd" 0.25))

(def DEFAULT_LINE_BACKGROUND_COLOR fx/WHITESMOKE)
(def DEFAULT_CURRENT_LINE_BACKGROUND_COLOR (fx/web-color "#F0F0FF"))
(def DEFAULT_CURRENT_LINE_BORDER_COLOR (fx/web-color "#E5E5FF"))

(def DEFAULT_GUTTER_INSETS (fx/insets 0.0, 14.0, 0.0, 14.0))
(def DEFAULT_GUTTER_TEXT_FILL (fx/web-color "#999"))
;(def DEFAULT_GUTTER_FONT (Font/font "monospace" FontPosture/ITALIC 13.))
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
  ;; returns "absolute" offset - compensating for scrolling
  ^double (getOffsetX [^int col])
  ;;offset directly from IScrollableText. Used by blocks.
  ;^double (getRelativeOffsetX [^int col])
  ^double (getMaxOffsetXforBlocks [])
  ^double (getGutterWidth [])
  ^int (getIndex []))

(definterface IScrollableText
  ^int (getColumn [^double offset-x])
  ;; Returns offsets relative to itself.
  ^double (getOffsetX [^int col])
  ^double (getMaxOffsetXforBlocks []))


(definterface IGutter
  ^double (getWidth [])
  (setText [^String s]))


(defn- new-row-gutter
  "AKA (left) margin, graphic, number-row.
  Let's use this as a general data-carrier for the line"
  []
  (let [nr-label
        (doto (Label.)
          (.setFont DEFAULT_GUTTER_FONT)
          (.setBackground DEFAULT_GUTTER_BACKGROUND)
          (.setPrefHeight (+ 2.0 ^double DEFAULT_LINE_HEIGHT))
          (.setTextFill DEFAULT_GUTTER_TEXT_FILL)
          (.setPadding DEFAULT_GUTTER_INSETS)
          (.setBorder DEFAULT_GUTTER_BORDER))]

       (proxy [Group IGutter] [(fxj/vargs nr-label)]
         (getWidth []
           (.layout this)
           (.getWidth nr-label))
         (setText [s]
           (.setText nr-label s)))))


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
  [texts ^long col]
  (if (zero? col)
    0.0
    (try
      (-> ^Text (get texts (dec col))
          .getBoundsInParent
          .getMaxX)
      (catch NullPointerException _ 0.0))))


(defn- calculate-max-offset-for-block
  "Returns the offset-x of where the block should end.
  Should not include newline-end"
  [chars texts]
  (if (empty? chars)
    0.0
    (let [col (- (count chars) (if (ut/newline-end? chars) 3 2))]
      (try
        ;; 'min' is for safety in case something has changed underfoot.
        ;(-> ^Text (get texts (min col (dec (count texts)))))
        (-> ^Text (get texts col)
            .getBoundsInParent
            .getMaxX)
        (catch NullPointerException _ 0.0)))))

(defn- max-offset-x
  "Called only from 'max-offset-x-mem'"
  [^VirtualFlow flow first-row last-row]
  (try
    (let [cells
          (map #(.getCell flow %)  ;; May throw exception
               (range first-row (inc ^int last-row)))
          lengths
          (mapv #(.getMaxOffsetXforBlocks ^IRowCell %)
                cells)]
      (apply max lengths))
    (catch IndexOutOfBoundsException _ 0.0)))

(defn max-offset-x-mem
  "Implements a memoize functionality, but using an atom from state, which gets reset whenever blocks reset."
  [^VirtualFlow flow first-row last-row mem_]
  (let [k [first-row last-row]
        x (@mem_ k)]
    (if x
        x
        (let [x (max-offset-x flow first-row last-row)]
          (swap! mem_ assoc k x)
          x))))


(defn find-block-spans [ranges mem_ row flow texts]
  (let [;; Get the offsets for start and end.
        spans
        (map #(let [{[frow fcol] :first [lrow lcol] :last} %
                    first?  (= row frow)
                    last?   (= row lrow)
                    start-x (calculate-offset texts fcol)
                    end-x   (if last?
                              (calculate-offset texts (inc ^int lcol))
                              (max-offset-x-mem flow frow lrow mem_))]
                [start-x end-x first? last?])
             ranges)]
    spans))


(defn set-blocks [^StackPane blocks-pane all-ranges mem_ row flow texts]
  (->  blocks-pane .getChildren .clear)
  (let [ranges (get all-ranges row)]
    (when-not (empty? ranges)
      (let [h DEFAULT_LINE_HEIGHT
            spans (find-block-spans ranges mem_ row flow texts)]

        (doseq [[^double x1 ^double x2 first? last?] spans]
          ;(println (format "  ## row %s : %s" row span))
          (let [b 0.5
                padding-left 0.5 ;; slightly more generous to the left
                x (- x1 padding-left)
                y 0
                w (+ ^double (* (- x2 x)) padding-left)
                block (doto (Region.)
                       (.setMaxSize w h)
                       (fx/set-translate-XY [x y])
                       (fx/set-background DEFAULT_BLOCK_COLOR)
                       (.setBorder
                         (fx/make-border DEFAULT_LINE_BACKGROUND_COLOR [0 0 0 b])))]
            (-> blocks-pane .getChildren (.add block))))))))


(defn- set-marks
  "Inserts and lays out markings (caret, anchor, select) if any, on the passed-in pane."
  [^StackPane pane {:keys [caret anchor caret-pos anchor-pos lines]} row chars texts]
  ;(println "view/set-marks " "o-marks:" (dissoc o-marks :lines) "row:" row)
  (let [
        [crow ccol] caret-pos
        [arow acol] anchor-pos

        [low ^int high] (sort [caret anchor])
        do-mark? (partial u/in-range? low (dec high))

        ^int row-index (st/location->index-- lines [row 0])]

    (loop [x 0.0 i 0 nodes texts chars chars]
      (when-let [n ^Text (first nodes)]
        (let [w (-> n .getBoundsInParent .getWidth)]
          (when (do-mark? (+ row-index i))
            (let [marking (selection-background-factory w DEFAULT_LINE_HEIGHT (first chars))]
              (.setTranslateX ^Node marking (- x 0.5)) ;; offset half pixel to left
              (-> pane .getChildren (.add marking))))
          (recur (+ x w) (inc i) (next nodes) (next chars)))))

    (when (= arow row)
      (let [anchor (anchor-factory DEFAULT_LINE_HEIGHT)]
        (.setTranslateX anchor (- ^double (calculate-offset texts acol) 0.25))
        (-> pane .getChildren (.add anchor))))

    (when (= crow row)
      (let [caret ^Node (DEFAULT_CURSOR_FACTORY DEFAULT_LINE_HEIGHT)]
        (.setTranslateX caret (- ^double (calculate-offset texts ccol) 1.0)) ;; negative offset for cursor width
        (-> pane .getChildren (.add caret))))))


(defn- highlight-row [^StackPane pane current-row?]
  (let []
    (if current-row?
      (doto pane
        (.setBorder (fx/make-border  DEFAULT_CURRENT_LINE_BORDER_COLOR [1 0 1 0]))
        (.setBackground (fx/color-background DEFAULT_CURRENT_LINE_BACKGROUND_COLOR))
        (.setMaxHeight (dec DEFAULT_LINE_HEIGHT)))
      (doto pane
        (.setBorder (fx/make-border  DEFAULT_LINE_BACKGROUND_COLOR [1 0 1 0]))
        (fx/set-background DEFAULT_LINE_BACKGROUND_COLOR)
        (.setMaxHeight (dec DEFAULT_LINE_HEIGHT))))))


(defn- set-marks-and-line
  "If the row is in the set, then delegates the task"
  [line-background-pane
   ^StackPane marks-pane
   {:keys [current-row-p? marked-row-p?] :as derived}
   row chars texts]

  (highlight-row line-background-pane (current-row-p? row))

  (->  marks-pane .getChildren .clear)
  (when (marked-row-p? row)
    (set-marks ^StackPane marks-pane derived row chars texts)))


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


(defn ensure-caret-visible [^VirtualFlow flow state]
  (let [[^long row col] (:caret-pos state)
        cell (.getCell flow row)
        ;; The "absolute" offset (of the caret) - i.e. number of pixels from the left of the flow
        ^double offset-x (.getOffsetX ^IRowCell cell col)
        ^double gutter-w (.getGutterWidth ^IRowCell cell)
        ;; How much has been scrolled
        ^double scrolled-x (-> flow .breadthOffsetProperty .getValue)
        flow-w (.getWidth flow)
        ;; The width between the gutter and the right side of the flow - i.e. the visible text area width
        main-w (- flow-w gutter-w)
        ;; We want to keep the caret from touching the outer borders of 'main-w'
        visible-padding 12.0
        ;; Is the caret visible between the right of the gutter and and the right edge of the flow?
        col-visible? (< (+ gutter-w visible-padding) offset-x (- flow-w visible-padding))
        ;; If we need to scroll (horizontally),
        ;; we need want to pass inn a bounding-box which should be made visible.
        ;; This works (through thinking + trial-and-error). Please update this comment with a logical explanation.
        bounding-x (- (+ offset-x scrolled-x) gutter-w (/ main-w 3))
        ;; It should be as wide as the 'main-w'
        bounding-w main-w
        bounding-box (BoundingBox. bounding-x 0 bounding-w DEFAULT_LINE_HEIGHT)

        ;; We also want the caret to be vertically visible.
        ;; And we don't want it to reach the very top or bottom row if avoidable.
        ;; So get the current first and last visible rows.
        visible-cells (.visibleCells flow)
        ^int first-visible-row (.getIndex ^IRowCell (first visible-cells))
        ^int last-visible-row (.getIndex ^IRowCell (last visible-cells))]

    (when-not col-visible?
      ;; Scroll horizontally.
      (.show flow row bounding-box))
    (when (<= row (inc first-visible-row))
      ;; Scroll up.
      (.show flow (dec row)))
    (when (>= row (dec last-visible-row))
      ;; Scroll down.
      (.show flow (inc row)))))


(defn- new-scrolling-part [gutter text-pane marks-pane blocks-pane scroll-offset_ chars texts texts-width]
  (let [
        insets ^Insets DEFAULT_LINE_INSETS
        inset-left (.getLeft  insets)
        inset-right (.getRight insets)

        scrolling-pane
        (doto
          (proxy [StackPane IScrollableText] [(fxj/vargs blocks-pane marks-pane text-pane)]
            ;; Impelements IScrollableText
            (getColumn [^double offset-x] ;; offset-x already considers scrolled offset
              (let [^double  gw (.getWidth gutter)
                    offset (- offset-x gw inset-left)
                    col
                    (if (<  (- offset-x ^double @scroll-offset_) gw) ;; offset-x is in/under in gutter.
                      :gutter
                      (calculate-col offset texts))]
                col))
            ;; Impelements IScrollableText
            (getOffsetX [col]
              (+ ^double (calculate-offset texts col)
                 inset-left))
            (getMaxOffsetXforBlocks []
             (+ ^double (calculate-max-offset-for-block chars texts)
                inset-left)))

          (.setAlignment Pos/CENTER_LEFT)
          (.setPrefHeight DEFAULT_LINE_HEIGHT)
          (.setPrefWidth (+ inset-left ^double texts-width inset-right))
          (.setPadding insets))]

    scrolling-pane))


(defn new-line-cell [state_ scroll-offset_ flow_ chars]
  (let [k (Object.)

        row_ (atom -1)

        line-background-pane
        (Pane.)

        gutter
        (new-row-gutter)

        set-gutter-text
        #(.setText gutter ((:line-count-formatter @state_) (inc ^int @row_)))

        text-pane
        (doto ^StackPane (fx/stackpane)
          (.setAlignment Pos/CENTER_LEFT))

        [texts-width texts]
        (insert-and-layout-chars-as-texts text-pane chars)

        marks-pane
        (doto ^StackPane (fx/stackpane)
          (.setAlignment Pos/CENTER_LEFT))

        blocks-pane
        (doto ^StackPane (fx/stackpane)
          (.setAlignment Pos/CENTER_LEFT))

        scrolling-part
        (new-scrolling-part gutter text-pane marks-pane blocks-pane  scroll-offset_ chars texts texts-width)

        node
        (proxy [Region] []
          ;; @override
          (computeMinWidth [^double _]
            (.computePrefWidth this -1.0))
          ;; @override
          (computePrefWidth [^double _]
            (.layout ^Region this)
            (let [insets  ^Insets (.getInsets ^Region this)]
              (+ ^double (.getWidth gutter)
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
                  go @scroll-offset_]
              (.resizeRelocate ^StackPane scrolling-part gw 0 (- w gw) h)
              (.resizeRelocate ^Region gutter go 0 gw h)
              (.resizeRelocate  line-background-pane go 0 w h))))]

    (-> node .getChildren (.setAll  (fxj/vargs-t Node
                                                 line-background-pane
                                                 scrolling-part
                                                 gutter)))

    (add-watch scroll-offset_ k (fn [_ _ _ _] (.requestLayout node)))

    (add-watch state_ k
               (fn [_ _ {prev-digits :line-count-digits prev-blocks :blocks}
                        {digits :line-count-digits blocks :blocks :as state}]
                 (when (not= prev-digits digits)
                   (set-gutter-text))
                 (set-marks-and-line line-background-pane marks-pane state @row_ chars texts)
                 (when (not= prev-blocks blocks)
                   (set-blocks blocks-pane (:block-ranges state) (:max-offset-x-mem_ state) @row_ @flow_ texts))
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
          (set-gutter-text)
          (set-marks-and-line line-background-pane marks-pane @state_ @row_ chars texts)
          (set-blocks blocks-pane (:block-ranges @state_) (:max-offset-x-mem_ @state_) @row_ @flow_ texts)
          (.requestLayout node)))
      ;; implements
      (dispose [_]
        (remove-watch scroll-offset_ k)
        (remove-watch state_ k))
      IRowCell
      ;; implements
      (getColumn [_ offset-x]
        (.getColumn scrolling-part offset-x))
      ;; implements
      (getOffsetX [_ col]
        (-
         (+ ^double (.getWidth gutter)
            ^double (.getOffsetX scrolling-part col))
         ^double @scroll-offset_))
      ;; implements
      (getMaxOffsetXforBlocks [_]
        (.getMaxOffsetXforBlocks scrolling-part))
      ;; implements
      (getGutterWidth [_]
        (.getWidth gutter))
      ;; implements
      (getIndex [_]
        @row_))))


