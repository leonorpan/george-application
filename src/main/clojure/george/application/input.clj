;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.input
  (:require
    [clojure.string :as cs]
    [george.javafx :as fx]
    [george.core.history :as hist]
    [george.application.repl :as repl]
    [george.javafx.java :as fxj]
    [george.code.core :as gcode]
    [george.util :as gu]
    [george.application.output :as output]
    [george.util :as u]
    [george.application.eval :as eval])
  (:import (javafx.scene.input KeyEvent)))





(defn- do-run [code-area repl-uuid current-history-index-atom ns-textfield clear? eval-button interrupt-button source-file]
  (let [input (gcode/text code-area)
        update-ns-fn #(fx/later (.setText ns-textfield %))
        eval-id (gu/uuid)]
    (if (cs/blank? input)
      ;; then
      (println)
      ;; else
      (fx/later  ;; GUI interatactions must be on a JavaFX render thread
        (.setDisable eval-button true)

        (doto interrupt-button
          (.setDisable  false)
          (.setOnAction
            (fx/event-handler
              ;(println "Interrupting:" eval-id)
              (repl/eval-interrupt eval-id)
              (output/output :system "Interrupted!\n"))))

        (fxj/daemon-thread
          (try
            (eval/read-eval-print-in-ns
              input
              (.getText ns-textfield)
              eval-id
              source-file
              update-ns-fn)


            ;; handle history and clearing
            (hist/append-history repl-uuid input)
            (reset! current-history-index-atom -1)
            (when clear? (fx/later (.clear code-area)))

            (catch Exception e
              (.printStackTrace e))
            (finally
              ;; No matter what, I need to be able to eval again
              (fx/later
                (.setDisable interrupt-button true)
                (.setDisable eval-button false)
                (-> code-area .getScene .getWindow .requestFocus)))))))))



(defn- input-scene [ns source-file]
  (let [
        repl-uuid (gu/uuid)

        current-history-index-atom (atom -1)

        ns-label
        (doto
          (fx/label (or ns "user"))
          ( .setStyle "
                    -fx-font: 12 'Source Code Pro Regular';
                    -fx-text-fill: gray;"))

        code-area
        (doto (gcode/->codearea))

        do-history-fn
        (fn [direction global?]
          (hist/do-history code-area repl-uuid current-history-index-atom direction global?))

        clear-checkbox
        (fx/checkbox "Clear on 'Eval'"
                     :tooltip "If selected, code is cleared when 'Eval' is  triggered (button or keyboard shortcut).")

        interrupt-button
        (doto
          (fx/button "X"
                     :width 30
                     :tooltip "Interrupt current 'Eval'")
          (.setDisable true))


        run-button
        (fx/button
          "Eval"
          :width 130
          :tooltip (format
                     "Run code, then clear if checkbox ckecked.          %s-ENTER
  Run code, then do the inverse of checkbox selection.   SHIFT-%s-ENTER" u/SHORTCUT_KEY u/SHORTCUT_KEY))


        do-run-fn
        (fn [inverse-clear]  ;; do the oposite of clear-checkbox
          (let [clear-checked
                (.isSelected clear-checkbox)
                do-clear
                (if inverse-clear (not clear-checked) clear-checked)]
            (do-run code-area repl-uuid current-history-index-atom ns-label do-clear run-button interrupt-button source-file)))

        prev-button
        (doto
          (fx/button
            (str  \u25B2)  ;; up: \u25B2,  left: \u25C0
            :onaction #(do-history-fn hist/PREV false)
            :tooltip (format
                       "Previous 'local' history.          %s-LEFT
Previous 'global' history.   SHIFT-%s-LEFT" u/SHORTCUT_KEY u/SHORTCUT_KEY)))


        next-button
        (doto
          (fx/button
            (str \u25BC)  ;; down: \u25BC,  right: \u25B6
            :onaction #(do-history-fn hist/NEXT false)
            :tooltip (format
                       "Next 'local' history.          %s-RIGHT
Next 'global' history.   SHIFT-%s-RIGHT" u/SHORTCUT_KEY u/SHORTCUT_KEY)))

        button-box
        (fx/hbox
          prev-button
          next-button
          (fx/region :hgrow :always)
          clear-checkbox
          (fx/region :hgrow :always)
          interrupt-button
          run-button
          :spacing 3
          :alignment fx/Pos_TOP_RIGHT
          :insets [5 0 0 0])

        border-pane
        (fx/borderpane
          :center code-area
          :top ns-label
          :bottom button-box
          :insets 10)

        scene
        (doto
          (fx/scene border-pane :size [500 200])
          (fx/add-stylesheets "styles/codearea.css"))

        key-pressed-handler
        (fx/key-pressed-handler {
                                 #{:SHORTCUT :UP} #(do-history-fn hist/PREV false)
                                 #{:SHIFT :SHORTCUT :UP} #(do-history-fn hist/PREV true)

                                 #{:SHORTCUT :DOWN} #(do-history-fn hist/NEXT false)
                                 #{:SHIFT :SHORTCUT :DOWN} #(do-history-fn hist/NEXT true)

                                 #{:SHORTCUT :ENTER} #(do-run-fn false)
                                 #{:SHIFT :SHORTCUT :ENTER} #(do-run-fn true)})]

    (.setOnAction run-button (fx/event-handler (do-run-fn false)))

    (.addEventFilter border-pane KeyEvent/KEY_PRESSED key-pressed-handler)
    ;; TODO: ensure code-area alsways gets focus back when focus in window ...
    ;; TODO: colorcode also when history is the same
    ;; TODO: nicer tooltips.  (monospace and better colors)

    scene))



;; For Input stage layout
(defonce ^:private input-vertical-offset (atom 0))
(defonce ^:private input-horizontal-offset (atom 0))
(defn next-input-vertical-offset [] (swap! input-vertical-offset inc))
(defn next-input-horizontal-offset [] (swap! input-horizontal-offset inc))


(defn new-input-stage [& [ns]]
  ;; TODO: consolidate/fix integrations/dependencies
  ;; TODO: add interupt-posibility (button) to/for run-thread

  (fxj/thread (repl/session-ensured! true))
  (let [
        repl-nr
        (hist/next-repl-nr)

        scene (input-scene ns (str \" "Input " repl-nr \"))

        screen-WH (-> (fx/primary-screen) .getVisualBounds fx/WH)

        horizontal-offset (* (next-input-horizontal-offset) 5)
        vertical-offset (* (next-input-vertical-offset) 20)

        stage
        (fx/now
          (doto (fx/stage
                  :title (format "Input %s" repl-nr)
                  :scene scene
                  :sizetoscene true
                  :location [(- (first screen-WH) (.getWidth scene) 30 horizontal-offset)
                             (+ 80 vertical-offset)])))]

    stage))


