;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.editor.state
  (:require
    [clojure.string :as cs]
    [clojure.pprint :refer [pprint]]
    [clj-diff.core :as diff]
    [george.util :as u]
    [george.editor.buffer :as b]
    [george.javafx :as fx]
    [clojure.core.rrb-vector :as fv])
  (:import
    (javafx.collections FXCollections ObservableList)
    (javafx.scene.input ClipboardContent Clipboard)
    (java.util List)
    (clojure.core.rrb_vector.rrbt Vector)
    (clojure.lang PersistentVector Keyword IFn Atom)))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(declare
  index->location--
  index->location_
  update-buffer_
  set-marks_
  invalidate-derived_
  update-derived_
  ensure-derived_
  caret-anchor_
  apply-formatter_
  do-update-list_)



(defn- digits
  "Returns number of digits for 'n'."
  [n]
  (if-not n
    0
    (if (zero? ^int n)
      1
      (inc (int (Math/log10 n))))))
;(println (digits nil))
;(println (digits 0))
;(println (digits 1))
;(println (digits 10))


(defn- line-count-format-str
  "Returns a format-string (Java style) for right-aligned number padded with blanks."
  ;; Thank you, Victor.
  [digits]
  (str "%1$" digits "d"))


(defn- new-line-count-formatter
  "Returns a 1-arg function which formats a string based on number of passed-in digits.
  (See 'format-str'.)"
  [digits]
  #(format (line-count-format-str digits) %))


(defn update-lines_ [{:keys [buffer line-count line-count-digits line-count-formatter] :as state}]
  (let [lines (b/split-buffer-lines buffer)
        cnt (count lines)
        new-line-count? (not= cnt line-count)
        digits (if new-line-count? (digits cnt) line-count-digits)
        formatter (if new-line-count? (new-line-count-formatter digits) line-count-formatter)]
    (-> state
        invalidate-derived_
        (assoc :lines lines
               :line-count cnt
               :line-count-digits digits
               :line-count-formatter formatter))))


(defn invalidate-lines_ [state]
  (-> state
    (assoc :lines nil)
    ;;; When lines are invalidated, then positions based on those must also be invalidated.
    invalidate-derived_))


(defn- ensure-lines_ [state]
  (if (:lines state)
    state
    (update-lines_ state)))


(defn- new-state_ [^Vector buffer ^String line-sep content-type content-formatter]
  (let [buf (or buffer (fv/vector))
        lines (b/split-buffer-lines buf)
        olist (FXCollections/observableArrayList ^List lines)
        state
        {
         ;; These are the actual values that the state must have.
         :buffer   buf
         :list     olist
         :line-sep (or line-sep "\n")
         :caret    0
         :anchor   0
         :prefcol  0  ;; used when up/down cause cursor to shift sideways.
         :content-type content-type
         :content-formatter content-formatter
         ;; These are derived values. They are initially nil.
         ;; They are "invalidated" by setting them back to nil.

         ;;  Is invalidated only when the buffer changes.
         :lines        nil  ;; A vector of Vectors.
         :line-count   nil  ;;  ^int (count lines)
         :line-count-digits    nil ;; ^int the number of digits in the line-count
         :line-count-formatter nil ;; IFn - a 1-arg fn which formats the passed-in line-index

         ;; These are invalidated both when the buffer changes or when caret or anchor changes
         :caret-pos       nil  ;; [^int row ^int col]
         :anchor-pos      nil  ;;  - '' -
         :caret-pos-prev  nil  ;; For use by "update-markings"
         :anchor-pos-prev nil  ;;  - '' -
         :update-marking-rows nil}]  ;;  A 'set' of row numbers e.g. '#{0 1 2}',
                                     ;; indicating which ones need markings (selection and marks) repainted.
    (-> state
        ;; TODO: Should be '(apply-formatter_ true)' - but only when colorcoding is in place - to show where the error is!
        (apply-formatter_ false)
      ensure-derived_)))


(defn new-state-atom [^Vector buffer ^String line-sep ^Keyword content-type ^IFn content-formatter]
  (atom (new-state_ buffer line-sep content-type content-formatter)))


(defn- calculate-marking-rows
  "Calculates which rows need to re-layout their markings.
  Returns a set of row-indicies, i.e. '#{0,1,3}'"
  [[crow _] [arow _] {[crow0 _] :caret-pos-prev [arow0 _] :anchor-pos-prev}]
  (let [[^int low ^int high] (sort [crow arow])]
    (if-not crow0
      (set (range low (inc high)))
      (let [[low0 high0] (sort [crow0 arow0])]
        (set (range (min low ^int low0) (inc  (max high ^int high0))))))))


(defn- update-derived_
  "Re-calculates all derived values.
  Make sure to call on state after any alterations.
  It will in turn update the states list when it is done."
  ;; TODO: Maybe called async in future.
  [state]
  ;(pprint ["/update-derived_ partial state:" (dissoc state :buffer :lines :list)])
  (let [state (ensure-lines_ state)
        [caret anchor] (caret-anchor_ state)
        lines (:lines state)
        cpos (index->location-- lines caret)
        apos (index->location-- lines anchor)]

    (assoc state
           :caret-pos cpos
           :anchor-pos apos
           :update-marking-rows (calculate-marking-rows cpos apos state))))


(defn- invalidate-derived_ [state]
  ;; don't invalidate twice, because then we loose the :update-marking-rows-prev
  ;(pprint ["/invalidate-derived_ partial state:" (dissoc state :buffer :lines :list)])
  (if-not (:caret-pos state)
    state
    (assoc state :caret-pos nil
                 :anchor-pos nil
                 :caret-pos-prev (:caret-pos state)
                 :anchor-pos-prev (:anchor-pos state)
                 :update-marking-rows nil)))


(defn ensure-derived_ [state]
  (if (:caret-pos state)
    state
    (update-derived_ state)))


;;;; GETTERS / CALCULATORS


(defn buffer_ [state]
  (:buffer state))


(defn buffer [^Atom state_]
  (buffer_ @state_))


(defn text [^Atom state_]
  (-> state_ buffer (#(String. (char-array %)))))


(defn- set-text_ [state ^String txt & [caret anchor]]
  (let [buffer (buffer_ state)
        edits (diff/diff buffer (seq txt))
        buffer (diff/patch buffer edits)]
    (-> state
        (update-buffer_ (constantly buffer))
        (set-marks_ (or caret 0) true (or anchor true) (or anchor caret))
        (apply-formatter_ false)
        do-update-list_)))

(defn set-text [^Atom state_ ^String txt]
  (swap! state_ set-text_ txt))

(defn- observable-list_ [state]
  (:list state))


(defn observable-list [^Atom state_]
  (observable-list_ @state_))


(defn caret_ [state]
  (:caret state))


(defn anchor_ [state]
  (:anchor state))


(defn caret-anchor_ [state]
    [(caret_ state) (anchor_ state)])


(defn prefcol_ [state]
  (:prefcol state))


(defn lines_ [state]
  (-> state :lines))

(defn line_ [state row]
  (-> state :lines (nth row)))


(defn length_ [state]
  (count (buffer_ state)))


(defn row->col0-index--
  "Returns the index for the beginning of the 'row'"
  [lines row]
  (let []
    (loop [cnt 0 curr-row 0]
      (if (= row curr-row)
        cnt
        (recur
          (+ cnt (count (lines curr-row)))
          (inc curr-row))))))


(defn row->col0-index_
  "Returns the index for the beginning of the 'row'"
  [state row]
  (row->col0-index-- (lines_ state) row))


(defn location->index-- [lines [row col]]
  ;(println "/location->index--" "lines:" lines "location:" [row col])
  (+ ^int (row->col0-index-- lines row) ^int col))


(defn location->index_ [state [row col]]
  (+ ^int (row->col0-index_ state row) ^int col))


(defn index->location-- [^PersistentVector lines index]
  (let []
    (loop [ix ^int index
           row 0]
      (let [ln (lines row)
            ln-len (count ln)
            ix-red (- ix ln-len)] ;; reduce index

        (if (pos? ix-red) ;; We have not overshot
          (recur ix-red (inc row))
          (if (and (= ln-len ix) (= (last ln) \newline))
            [(inc row) 0]
            [row ix]))))))


(defn index->location_ [state index]
  (index->location-- (lines_ state) index))


;;;; SETTERS


(defn set-caret_ [state index]
  (assoc state :caret index))


(defn set-anchor_ [state index]
  (assoc state :anchor index))


(defn set-prefcol_ [state caret]
  (assoc state :prefcol (second (index->location_ state caret))))


(defn reset-prefcol_ [state]
  (set-prefcol_ state (caret_ state)))


(defn set-marks_ [state index move-prefcol? move-anchor? & [anchor-index]]
  ;(println "/set-marks_" "_" index move-prefcol? move-anchor? anchor-index)
  (let [clamper #(u/clamp-int 0 %  (length_ state))
        anc
        (or anchor-index index)
        ;_ (println "  ## anc:" anc)
        state
        (-> state
            ensure-lines_
            (set-caret_ (clamper index))
            (#(if move-prefcol? (set-prefcol_ % (clamper index)) %))
            (#(if move-anchor? (set-anchor_ % (clamper anc)) %))
            invalidate-derived_)]

    state))

(defn- apply-formatter_
  "Applies formatter on state, if a formatter has been set.
  Optional 'strict?' is used when state is changed with data from the \"outside\" - e.g. pasted, or read from file."
  [state & [strict? selection-start-line]]
  (if-let [formatter (:content-formatter state)]
    (formatter state strict? selection-start-line)

    ;; Else no formatting
    state))


(defn- move_ [state steps move-prefcol? move-anchor? move-caret-if-sel-reset?]
  (let [car (int (caret_ state))
        anc (int (anchor_ state))

        sel-reset? (and (not= car anc) move-anchor?)

        car1
        (u/clamp-int 0 (+ car ^int steps) (length_ state))

        car2
        (if (and sel-reset? (not move-caret-if-sel-reset?))
          (if (neg? ^int steps) (min car anc) (max car anc))
          car1)]

    (set-marks_ state car2 move-prefcol? move-anchor?)))


(defn- move-col_ [state steps move-prefcol? move-anchor? move-caret-if-sel-reset? aux]
  (let [limit? (= aux :limit)
        state (ensure-lines_ state)
        [row col] (index->location_ state (caret_ state))
        ln ((lines_ state) row)
        newline-end? (u/newline-end? ln)
        ln-len (count ln)
        steps1
        (if limit? (if (neg? ^int steps)
                     (- ^int col)
                     (- ln-len (if newline-end? 1 0) ^int col))
          steps)]
    (move_ state steps1 move-prefcol? move-anchor? move-caret-if-sel-reset?)))


(defn- move-row_ [state steps reset-anchor? aux]

  (if (= aux :limit)
    (let [^int car (caret_ state)
          ^int len (length_ state)
          steps1
          (if (neg? ^int steps)
            (- car)
            (- len car))]
      (move_ state steps1 true reset-anchor? true))

    (if (= aux :step)
      (let [
            state (ensure-lines_ state)
            [^int row _] (index->location_ state (caret_ state))
            ^int lns-cnt (:line-count  state)
            steps1
            (if (neg? ^int steps)
              (- row)
              (- lns-cnt 1 row))]
        (move-row_ state steps1 reset-anchor? nil))

      (let [
            state (ensure-lines_ state)
            ^int car (caret_ state)
            [^int row _] (index->location_ state car)
            pcol (prefcol_ state)
            lns (lines_ state)
            lns-cnt (count lns)
            row1 (+ row ^int steps)
            row2 (u/clamp-int 0 row1 (dec lns-cnt))
            ln (lns row2)
            newline-end? (= (last ln) \newline)
            ln-len (count ln)
            col1  (u/clamp-int 0 pcol (max 0 (int (if newline-end? (dec ln-len) ln-len))))
            col2
            (if (not= row1 row2)  ;; were were clamped! Move to end or row.
              (if (neg? ^int steps) 0 ln-len)
              col1)
            ^int index-new  (location->index_ state [row2 col2])]

        (move_ state (- index-new car)
              ;; reset prefcol if the move changes the col, or does not change the row
              (or (not= col1 col2)
                  (and (zero? ln-len) (= row row2)))
              reset-anchor?
              true)))))


;;;; HANDLERS / LISTENERS


(defn- do-update-list_
  "Updates the observable-list  so it matches the buffer.
  It uses the Meyer's Diff Algorithm: http://simplygenius.net/Article/DiffTutorial1

  Make sure to require george.util-namspace, as installs the method 'diff/patch' for ObservableList."
  [state]
  (let [state (ensure-derived_ state)
        ^ObservableList olist (observable-list_ state)
        edit-script (diff/diff (vec olist) (lines_ state))]
    (diff/patch olist edit-script)
    state))


(defn- insert-at [buffer offset chars]
  (u/insert-at buffer offset chars))


(defn- replace-range [buffer start end chars]
  (u/replace-range buffer start end chars))


(defn- delete-range [buffer start end]
  (u/remove-range buffer start end))


(defn update-buffer_ [state f & args]
  ;(println "/update-buffer_"); f args)
  (let [buffer (buffer_ state)
        buffer (apply f (cons buffer args))]
        ;lines (b/split-buffer-lines buffer)]
    (-> state
        (assoc :buffer buffer)
        invalidate-lines_)))


(defn- keytyped_
  "Returns an updated state (or the old one)."
  [state ch]
  ;(prn "state/keytyped_" ch (int ch))
  (let [[^int car anc :as car-anc] (caret-anchor_ state)
        state
        (if (not= car anc) ;; there is a selection
          (let [[^int start end] (sort car-anc)]
             (-> state
                 (update-buffer_ replace-range start end [ch])
                 (set-marks_ (inc start) true true)))
          (-> state
              (update-buffer_ insert-at car [ch])
              (set-marks_ (inc car) true true)))]

    (-> state
        (apply-formatter_ false)
        do-update-list_)))


(defn- delete_
  "Handles deletes (backspace/forward-delete)"
  [state direction]

  (let [[^int car _ :as car-anc]
        (caret-anchor_ state)

        [start end]
        (sort car-anc)

        state
        (if (not= start end)
          (-> state
              (update-buffer_ delete-range start end)
              (set-marks_ start true true))

          (if (and (neg? ^int direction))
            ;; backspace
            (if (> car 0)
              (-> state
                  (update-buffer_ delete-range (dec car) car)
                  (set-marks_ (dec car) true true))
              state)
            ;; delete (forward)
            (if (< car ^int (length_ state))
              (-> state
                  (update-buffer_ delete-range car (inc car)))
              state)))]

       (-> state
           (apply-formatter_ false)
           do-update-list_)))

(defn- tabbable-rows_ [state]
  (let [[start end] (sort (caret-anchor_ state))
        [srow _] (index->location_ state start)
        [erow ecol] (index->location_ state end)]
    (if (= srow erow)
      [srow]
      (if (zero? ^int ecol)
        (range srow erow)
        (range srow (inc ^int erow))))))


(defn spaces-at-row-start-- [lines row]
  (let [line (lines row)]
    (loop [cnt 0 line line]
      (if-let [ch (first line)]
        (if (b/space-char? ch)
          (recur (inc cnt) (rest line))
          cnt)
        cnt))))

(defn spaces
  "Returns a vector containing 'n' space-chars"
  [n]
  (vec (repeat n \space)))


;; # Alternative TAB-action
;; ## Cursive
;; Any marker on the first (or only) line always move with the line.
;; Any marker on the last line never move, if more than one row.
;; The last line moves only if the marker is col != 0.
;; If no selection, moves no matter where caret is in line.
;; ## IntelliJ
;; If no selection, caret moves line only when before first text, otherwise inserts tabe expanded to spaces.
;; Moves ending mark with line, if line moves.
;; ## Parinfer demo - http://shaunlebron.github.io/parinfer/demo
;; Inserts spaces like IntelliJ
;; Moves starting mark with line if no selection or if selection starting col != 0
;; Moves ending mark to beginning of line if line moves.
;; ## George
;; If the mark is on a line that moves then the mark moves!
;; If end-line>start-line, but end-mark at col=0 the end-line does not move.
;; Tab always moves line, never inserts spaces.

(defn- tab_ [state]
  ;(println "/tab_")
  (let [TAB 2
        lines (lines_ state) ;; Get lines, as first update will invalidate them.
        rows (tabbable-rows_ state)
        ;_ (println "tabbable-rows:" rows)

        ;; calculate insertion points and data: row -> [index items]
        insertions
        (map #(vector (location->index-- lines [% 0])  TAB) rows)

        ^int insertions-sum
        (reduce + (map second insertions))

        ;; apply insertions to state
        state
        (reduce (fn [state [i cnt]]
                  (update-buffer_ state insert-at i (spaces cnt)))
                state
                (reverse insertions)) ;; reverse to avoid offset-issues for following insertions


        [^int c ^int a] (caret-anchor_ state)
        caret-first? (<= c a)
        caret-last? (>= c a)
        [^int crow _] (index->location-- lines c)
        [^int arow _] (index->location-- lines a)

        [c1 a1]
        (if (= crow arow)
          [(+ c TAB) (+ a TAB)]
          [(if caret-first?
               (+ c TAB)
               (+ c
                  insertions-sum
                  (if (zero? crow) (- TAB) 0)))
           (if (not caret-first?)
               (+ a TAB)
               (+ a
                  insertions-sum
                  (if (zero? arow ) (- TAB) 0)))])]


    (-> state
        (set-marks_ c1 true true a1)
        (apply-formatter_ false (first rows))
        do-update-list_)))



(defn test-code []
    (println "test-code"))
(def nothing "nothing")


(defn- clamp-untab--
  "Ensures that untab is not bigger than leading spaces. Adjusts nr of spaces to untab."
  [lines row ^long requested-dels]
  (let [^int spaces-cnt (spaces-at-row-start-- lines row)]
    (min spaces-cnt requested-dels)))


(defn- untab_ [state]
  ;; Algorithm:
  ;; Calculate possible untabs for each line, relative to the requested
  ;; Apply the untabs (in reverse).
  ;; Shift the start-marker the same as the first line
  ;; Sum the untabs and shift the end-tab - incl/excl tabbing if the end-line also was untabbed.

  ;; for the end-marker:
  ;; If I am at col0, then simply subtract all previous untabs.
  ;; Else
  ;; 1: What col am I at now.  Say 1 or 3
  ;; Subtract all previous untabs and current col to get col0.
  ;; Subtract all untabs, but do (max new-index col0-index)
  ;; su
  (let [UNTAB 2
        lines (lines_ state)
        rows (tabbable-rows_ state)

        deletions
        (map #(vector (location->index-- lines [% 0]) (clamp-untab-- lines % UNTAB)) rows)

        ;; apply deletions to state
        state
        (reduce (fn [state [^int i ^int cnt]]
                  (update-buffer_ state delete-range i (+ i cnt)))
                state
                (reverse deletions)) ;; reverse to avoid offset-issues for following insertions

        ^int deletions-sum
        (reduce + (map second deletions))
        ^int butlast-sum (reduce + (map second (butlast deletions)))

        [^int c ^int a] (caret-anchor_ state)
        caret-first? (<= c a)
        caret-last? (>= c a)

        [^int crow ^int ccol] (index->location-- lines c)
        [^int arow ^int acol] (index->location-- lines a)

        ^int first-sum (-> deletions first second)
        ^int butlast-sum (reduce + (map second (butlast deletions)))

        start-col0-clamper
        (fn [^long index ^long col]
            (let [col0-index (- index  col)
                  new-index (- index first-sum)]
                 (max col0-index new-index)))


        end-col0-clamper
        (fn [^long index ^long col]
            (let [col0-index (- index butlast-sum col)
                  new-index (- index deletions-sum)]
              (if (zero? col)
                (- index deletions-sum)
                (max col0-index new-index))))


        [^int c1 ^int a1]
        (if (= crow arow)
          [(start-col0-clamper c ccol)    (start-col0-clamper a acol)]
          (if caret-first?
            [(start-col0-clamper c ccol)  (end-col0-clamper a acol)]
            [(end-col0-clamper c ccol)    (start-col0-clamper a acol)]))]

    (-> state
        (set-marks_ c1 true true a1)
        (apply-formatter_ false (first rows))
        do-update-list_)))





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
          (update-buffer_ delete-range start end)
          (set-marks_ start true true)
          (apply-formatter_ false)
          do-update-list_))))


(defn- paste_ [state]
  (if-let [s (clipboard-str)]
    (let [[^int start end] (sort (caret-anchor_ state))
          len (count s)]
      (-> state
          (update-buffer_ replace-range start end (vec s))
          (set-marks_ (+ start len) true true)
          ;; TODO: Should be '(apply-formatter_ true)' - but only when colorcoding is in place - to show where the error is!
          (apply-formatter_ false)
          do-update-list_))))


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
    :tab
    (tab_ state)
    :untab
    (untab_ state)
    :selectall
    (-> state
      (set-marks_ (length_ state) true true 0)
      update-derived_)
    :cut
    (cut_ state)
    :copy
    (copy_ state)
    :paste
    (paste_ state)

    ;; else pull apart the keyword for types of navigation/selection
    (let [[typ dir aux] (-> kw name (cs/split #"-") (#(mapv keyword %)))
          move? (= typ :move)]
      (-> (condp = dir
              :left   (move-col_ state -1 true move? false aux)
              :right  (move-col_ state 1 true move? false aux)
              :up     (move-row_ state -1  move? aux)
              :down   (move-row_ state 1 move? aux)
              ;; default
              (do (println "  !! NO IMPL for" kw) state))

          (apply-formatter_ false)
          ensure-derived_))))

(defn- mouse-loc->index [state loc]
  (if (= loc :end)
    (length_ state)
    (let [[row col] loc
          gutter? (= col :gutter)
          ln ((lines_ state) row)
          newline-end? (= (last ln) \newline)
          col1 (cond
                 gutter? 0
                 (and newline-end? (= col (count ln))) (dec ^int col)
                 :default col)]
      (location->index_ state [row col1]))))


(defn- mouseaction_ [state  prev-e-typ e-typ typ loc]
  ;(println "/mouseaction_"  prev-e-typ e-typ typ loc)

  (let [index (int (mouse-loc->index state loc))
        gutter? (and (not= loc :end)
                     (let [[_ col] loc]  (= col :gutter)))

        [^int line-end-offset ^int line-end-offset-adjusted]
        (if gutter?
          (let [ln ((lines_ state) (first loc))
                cnt (count ln)
                newline-end? (= (last ln) \newline)]
            [cnt
             (if newline-end? (dec cnt) cnt)])
          [0 0])

        [caret anchor] (caret-anchor_ state)

        [^int low ^int high] (sort [caret anchor])

        [c1 a1 move?]
        (if (= typ :select)
          (cond
            (<= index low)  [index high true]
            (>= index high) [(+ index line-end-offset-adjusted) low true]
            :default        [(+ index line-end-offset-adjusted) anchor true])
          [index
           (if gutter? (+ index line-end-offset) nil) ;; must be nil, else anchor doesn't get set to index by 'set-marks'
           (and (= typ :move) (= e-typ :pressed))])]

       (-> state
           (set-marks_  c1 true move? a1)
           (apply-formatter_ false)
           ensure-derived_)))

(defn keypressed [^Atom state_ kw]
  (swap! state_ keypressed_ kw))


(defn keytyped [^Atom state_ ch]
  (swap! state_ keytyped_ ch))


(defn mouseaction [^Atom state_ prev-e-typ e-typ sel-typ loc]
  (swap! state_ mouseaction_ prev-e-typ e-typ sel-typ loc))



