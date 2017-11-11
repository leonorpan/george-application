;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.editor.state
  (:require [clojure.string :as cs]
            [george.util :as u]
            [george.editor.buffer :as b]
            [george.javafx :as fx])
  (:import (javafx.collections ObservableList FXCollections)
           (clojure.lang PersistentVector)
           (javafx.scene.input ClipboardContent Clipboard)
           (java.util List)))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* true)


;;;; CREATORS


(defn- new-marks []
  {:anchor 0
   :caret 0
   ;; used when up/down cause cursor to shift sideways.
   :prefcol 0})




(defn new-state [^PersistentVector buffer ^String line-sep ^ObservableList list]
  (let [buf (or buffer [])
        lst (if (and list (pos? (count list)))
              list
              (FXCollections/observableArrayList ^List (b/split-buffer-lines buf)))]
    {:buffer    buf
     :lines    (b/split-buffer-lines buf)
     :list     lst
     :line-sep (or line-sep "\n")
     :marks    (new-marks)}))



(declare index->location_)

(defn- derived-values [state]
  (let [{{:keys [caret anchor]} :marks} state]
    {:state state
     :caret caret
     :anchor anchor
     :caret-pos (index->location_ state caret)
     :anchor-pos (index->location_ state anchor)
     :line-count (-> state :lines count)}))


(defn new-line-count
  "Returns an atom which is updated whenever the linecount changes.
  The purpose is to re-format line-numbering when number of digits changes"
  [derived_]
  (let [line-count_ (atom 0)]
    (add-watch derived_ (Object.)
               (fn [_ _ prev-der der]
                 (when (not= (:line-count prev-der) (:line-count der))
                   (reset! line-count_ (:line-count der)))))
    line-count_))


(defn new-state-derived
  "Returns an atom which is updated with useful values whenever state changes.
  The purpose is to prevent each paragraph from calculating many of same values over and over."
  [state_]
  (let [derived_ (atom (derived-values @state_))]
    (add-watch state_
               (Object.)
               (fn [_ _ _ state]
                 (reset! derived_ (derived-values state))))
    derived_))





;;;; GETTERS / CALCULATORS

(defn- buffer_ [state]
  (:buffer state))

(defn- buffer [state_]
  (buffer_ @state_))


(defn- observable-list_ [state]
  (:list state))

(defn observable-list [state_]
  (observable-list_ @state_))


(defn caret_ [state]
  (-> state :marks :caret))

(defn anchor_ [state]
  (-> state :marks :anchor))

(defn caret-anchor_ [state]
  (let [{{c :caret a :anchor} :marks} state]
    [c a]))

(defn prefcol_ [state]
  (-> state :marks :prefcol))


(defn lines_ [state]
  (:lines state))


(defn line_ [state row]
  (nth (lines_ state) row))


(defn list_ [state]
  (:list state))


(defn length_ [state]
   (reduce + (map count (lines_ state))))

(defn row->index-col0_
  "Returns the index for the beginning of the 'row'"
  [state row]
  (let [lns (lines_ state)]
    (loop [cnt 0 curr-row 0]
      (if (= row curr-row)
        cnt
        (recur
          (+ cnt (count (get lns curr-row))) ;1) ;; add '1' for \newline
          (inc curr-row))))))

(defn location->index_ [state [row col]]
  (+ (row->index-col0_ state row) col))



(defn index->location_ [state index]
  (let [lns (lines_ state)]
    (loop [ix index
           row 0]
      (let [ln (lns row)
            ln-len (count ln)
            ix-red (- ix ln-len)] ;; reduce index

        (if (pos? ix-red) ;; We have not overshot
          (recur ix-red (inc row))
          (if (and (= ln-len ix) (= (last ln) \newline))
            [(inc row) 0]
            [row ix]))))))



;;;; SETTERS


(defn- set-anchor_ [state index]
  (assoc-in state [:marks :anchor] index))


(defn- reset-anchor_ [state]
  (set-anchor_ state (caret_ state)))


(defn- set-prefcol_ [state caret]
  (assoc-in state [:marks :prefcol]
            (second (index->location_ state caret))))

(defn- reset-prefcol_ [state]
  (set-prefcol_ state (caret_ state)))



(defn- set_ [state index move-prefcol? move-anchor? & [anchor-index]]
  (let [anc (or anchor-index index)

        state
        (assoc-in state [:marks :caret] index)
        state
        (if move-anchor? (set-anchor_ state anc) state)
        state
        (if move-prefcol? (set-prefcol_ state anc) state)]

    state))


(defn- move_ [state steps move-prefcol? move-anchor? move-caret-if-sel-reset?]
  (let [car (caret_ state)
        anc (anchor_ state)

        sel-reset? (and (not= car anc) move-anchor?)

        car1
        (u/clamp 0 (+ car steps) (length_ state))

        car2
        (if (and sel-reset? (not move-caret-if-sel-reset?))
          (if (neg? steps) (min car anc) (max car anc))
          car1)]

    (set_ state car2 move-prefcol? move-anchor?)))


(defn- move-col_ [state steps move-prefcol? move-anchor? move-caret-if-sel-reset? aux]
  (let [limit? (= aux :limit)
        [row col] (index->location_ state (caret_ state))
        ln ((lines_ state) row)
        newline-end? (u/newline-end? ln)
        ln-len (count ln)
        steps1
        (if limit? (if (neg? steps)
                     (- col)
                     (- ln-len (if newline-end? 1 0) col))
          steps)]
    (move_ state steps1 move-prefcol? move-anchor? move-caret-if-sel-reset?)))


(defn- move-row_ [state steps reset-anchor? aux]

  (if (= aux :limit)
    (let [car (caret_ state)
          len (length_ state)
          steps1
          (if (neg? steps)
            (- car)
            (- len car))]
      (move_ state steps1 true reset-anchor? true))

    (if (= aux :step)
      (let [
            [row _] (index->location_ state (caret_ state))
            lns-cnt (count (lines_ state))
            steps1
            (if (neg? steps)
              (- row)
              (- lns-cnt 1 row))]
        (move-row_ state steps1 reset-anchor? nil))

      (let [car (caret_ state)
            [row col :as loc] (index->location_ state car)
            ;_ (println "loc:" loc)
            pcol (prefcol_ state)
            lns (lines_ state)
            lns-cnt (count lns)
            ;_ (println "lns-cnt:" lns-cnt)
            row1 (+ row steps)
            ;_ (println "row1:" row1)
            row2 (u/clamp 0  row1 (dec lns-cnt))
            ;_ (println "row2:" row2)
            ln (lns row2)
            newline-end? (= (last ln) \newline)
            ln-len (count ln)
            ;_ (println "ln-len:" ln-len)
            col1  (u/clamp 0 pcol (max 0 (if newline-end? (dec ln-len) ln-len)))
            ;_ (println "col1:" col1)
            col2
            (if (not= row1 row2)  ;; were were clamped! Move to end or row.
              (if (neg? steps) 0 ln-len)
              col1)
            ;_ (println "col2:" col2)
            index-new (location->index_ state [row2 col2])]

        (move_ state (- index-new car)
              ;; reset prefcol if the move changes the col, or does not change the row
              (or (not= col1 col2)
                  (and (zero? ln-len) (= row row2)))
              reset-anchor?
              true)))))



;;;; HANDLERS / LISTENERS

(defn- do-update-list_ [state]
  (b/do-update-list (lines_ state) (observable-list_ state))
  state)


(defn update-buffer_ [state f & args]
  (let [buffer (apply f (concat [(buffer_ state)] args))
        lines (b/split-buffer-lines buffer)]
    (assoc state :buffer buffer :lines lines)))


(defn- keytyped_
  "Returns an updated state (or the old one)."
  [state ch]
  ;(prn "state/keytyped_" ch)
  (let [[car anc :as car-anc] (caret-anchor_ state)
        state
        (if (not= car anc) ;; there is a selection
          (let [[start end] (sort car-anc)]
             (-> state
                 (update-buffer_ b/replace-range start end [ch])
                 (set_ state (inc start) true true)))
          (-> state
              (update-buffer_ b/insert-at car [ch])
              (set_ (inc car) true true)))]

    (do-update-list_ state)))



(defn- delete_
  "Handles deletes (backspace/forward-delete)"
  [state direction]

  (let [[car _ :as car-anc]
        (caret-anchor_ state)

        [start end]
        (sort car-anc)

        state
        (if (not= start end)
          (-> state
              (update-buffer_ b/delete-range start end)
              (set_ start true true))

          (if (and (neg? direction))
            ;; backspace
            (if (> car 0)
              (-> state
                  (update-buffer_ b/delete-range (dec car) car)
                  (set_ (dec car) true true))
              state)
            ;; delete (forward)
            (if (< car (length_ state))
              (-> state
                  (update-buffer_ b/delete-range car (inc car)))
              state)))]

    (do-update-list_ state)))




(def CB (fx/now (Clipboard/getSystemClipboard)))

(defn clipboard-str []
  ;(println "  ## CB content types:" (fx/now (.getContentTypes CB)))
  (fx/now (.getString ^Clipboard CB)))


(defn set-clipboard-str [s]
  (let [cbc (doto (ClipboardContent.)
              (.putString s))]
    (fx/now (.setContent ^Clipboard CB cbc))))



(defn- selection_
  "Returns selection as vector of chars, else nil if no selection"
  [state]
  (let [[start end] (sort (caret-anchor_ state))]
    (when (not= start end)
      (subvec (buffer_ state) start end))))


(defn- copy_
  "Returns the selection which was copied onto the global clipboard if any, else nil."
  [state]
  (when-let [sel (selection_ state)]
      ;(println "sel:" (apply str sel))
      (set-clipboard-str (apply str sel)))
  state)

(defn- cut_ [state]
  (let [[start end] (sort (caret-anchor_ state))]
    (if (= start end)
      state
      (-> state
          copy_
          (update-buffer_ b/delete-range start end)
          (set_ start true true)
          do-update-list_))))


(defn- paste_ [state]
  (if-let [s (clipboard-str)]
    (let [[start end] (sort (caret-anchor_ state))
          len (count s)]
      (-> state
          (update-buffer_ b/replace-range start end (seq s))
          (set_ (+ start len) true true)
          do-update-list_))
    state))

;(defn copy [state_]
;  (copy_ @state_))

;(defn paste [state_]
;  (paste_ @state_))


(defn- keypressed_ [state kw]
  "Returns an updated state (or the old one)."
  ;(prn "state/keypressed_" kw)
  (condp = kw
    :enter
    (keytyped_ state \newline)
    :backspace
    (delete_ state -1)
    :delete
    (delete_ state 1)

    :selectall
    (set_ state (length_ state) true true 0)

    :cut
    (cut_ state)
    :copy
    (copy_ state)
    :paste
    (paste_ state)

    ;; else pull apart the keyword for types of navigation/selection
    (let [[typ dir aux :as res] (-> kw name (cs/split #"-") (#(mapv keyword %)))
          ;_ (println "  ## res:" res)
          ;_ (println "  ## aux:" aux)
          move? (= typ :move)]
      (let [state1
            (condp = dir
              :left   (move-col_ state -1 true move? false aux)
              :right  (move-col_ state 1 true move? false aux)
              :up     (move-row_ state -1  move? aux)
              :down   (move-row_ state 1 move? aux)
              ;; default
              (do (println "  !! NO IMPL for" kw) state))]
        state1))))





(defn- mouse-loc->index [state loc]
  (if (= loc :end)
    (length_ state)
    (let [[row col] loc
          gutter? (= col :gutter)
          ln ((lines_ state) row)
          newline-end? (= (last ln) \newline)
          col1 (cond
                 ;; TODO: Clicking on gutter should select whole line (placing caret at beginning).
                 gutter? 0
                 (and newline-end? (= col (count ln))) (dec col)
                 :default col)]
      (location->index_ state [row col1]))))




(defn- mouseaction_ [state  prev-e-typ e-typ typ loc]
  ;(println "/mouseaction_"  prev-e-typ e-typ typ loc)

  (let [index (mouse-loc->index state loc)
        gutter? (and (not= loc :end)
                     (let [[_ col] loc]  (= col :gutter)))
        ;_ (println "  ## gutter?" gutter?)

        [line-end-offset line-end-offset-adjusted]
        (if gutter?
          (let [ln ((lines_ state) (first loc))
                cnt (count ln)
                newline-end? (= (last ln) \newline)]
            [cnt
             (if newline-end? (dec cnt) cnt)])
          [0 0])
        ;_ (println "  ## line-end-offsets:" line-end-offset)

        [caret anchor] (caret-anchor_ state)

        [low high] (sort [caret anchor])

        [c1 a1 move?]
        (if (= typ :select)
          (cond
            (<= index low)  [index high true]
            (>= index high) [(+ index line-end-offset-adjusted) low true]
            :default        [(+ index line-end-offset-adjusted) anchor true])
          [index
           (if gutter? (+ index line-end-offset) nil) ;; must be nil, else anchor doesn't get set to index by 'set-marks'
           (and (= typ :move) (= e-typ :pressed))])]

    ;(println "  ## [c1 a1 move?]:" [c1 a1 move?])
    (set_ state c1 true move? a1)))


(defn keypressed [state_ kw]
  (swap! state_ keypressed_ kw))


(defn keytyped [state_ ch]
  (swap! state_ keytyped_ ch))


(defn mouseaction [state_ prev-e-typ e-typ sel-typ loc]
  (swap! state_ mouseaction_ prev-e-typ e-typ sel-typ loc))



