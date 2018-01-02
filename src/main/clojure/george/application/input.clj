; Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns george.application.input
  (:require
    [clojure.string :as cs]
    [environ.core :refer [env]]
    [george.javafx :as fx]
    [george.core.history :as hist]
    [george.application.repl :as repl]
    [george.javafx.java :as fxj]
    [george.util :as gu]
    [george.application.output :refer [oprintln]]
    [george.util :as u]
    [george.application.eval :as eval]
    ;[george.code.paredit :as paredit]
    ;[george.code.codearea :as ca]
    [george.editor.core :as ed]
    [george.application.ui.layout :as layout])
  (:import
    [javafx.scene.input KeyEvent MouseEvent]
    [java.net SocketException]
    [javafx.scene.control Tab Button TabPane]
    [javafx.scene Node Scene]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defn- request-focus [^Node focusable]
  (try
    (fxj/thread (Thread/sleep 300) (fx/later (.requestFocus focusable)))
    ;; The focusable may be gone as the interrupt being a result of closing it.
    (catch NullPointerException e nil)))


(defn do-eval [code-str ^Button run-button ^Button interrupt-button ns-fn update-ns-fn file-name focusable post-success-fn]
  (let [
        eval-id (gu/uuid)]
    (if(cs/blank? code-str)
      (do
        (println)
        (request-focus focusable))
      (do
        ;; update UI
        (fx/later
          (.setDisable run-button true)
          (doto interrupt-button
                (.setDisable  false)
                (fx/set-onaction
                  #(do (repl/interrupt-eval eval-id)
                       (oprintln :system-em "Interrupted!")))))
        ;; do execution
        (fxj/daemon-thread
          (try
            (eval/read-eval-print-in-ns code-str (ns-fn) eval-id file-name update-ns-fn)
            (when post-success-fn (post-success-fn))
            ;; handle possible problems
            (catch SocketException e
              (oprintln :err (format "%s: %s" (.getClass e) (.getMessage e)))
              (oprintln :err "  ... possibly due to session or server restart."))
            (catch Exception e
              (println "ØØØØØØØØH")
              (.printStackTrace e))

            (finally
              ;; Update UI
              (fx/later
                (.setDisable interrupt-button true)
                (.setDisable run-button false)
                (request-focus focusable)))))))))


(defn history-wrapper
  "Handles history, then calls passed-in eval-fn"
  [repl-uuid current-history-index_ code-str eval-fn]
  (hist/append-history repl-uuid code-str)
  (reset! current-history-index_ -1)
  (eval-fn))


(defn- run-tooltip [clearable?]
  (if clearable?
    (format
      "Run code, then clear if checkbox ckecked.                  %s-ENTER
Run code, then do the inverse of checkbox selection. SHIFT-%s-ENTER" u/SHORTCUT_KEY u/SHORTCUT_KEY)
    (format "Run code.  %s-ENTER."  u/SHORTCUT_KEY)))


(defn run-button [& [clearable?]]
  (fx/button
    "Run"
    :width 130
    :tooltip (run-tooltip clearable?)))


(defn interrupt-button []
  (doto
    (fx/button "X"
               :width 30
               :tooltip "Interrupt current 'Run'")
    (.setDisable true)))


(defn ns-label []
  (doto (fx/label)
    (.setStyle "-fx-font: 14 'Source Code Pro Medium';
                            -fx-text-fill: gray;
                            -fx-padding: 3;")))


(defn set-ns-label-fn [label]
  (fn [ns]
    (fx/later
      (doto label
        (.setText ns)
        (fx/set-tooltip (str "*ns* " ns))))))


(defn new-input-root [file-name selected_ focused_ tab & {:keys [ns]}]
  (let [repl-uuid (gu/uuid)
        current-history-index_ (atom -1)

        ns-label
        (ns-label)
        update-ns-fn
        (set-ns-label-fn ns-label)
        _ (update-ns-fn (or ns "user"))

        editor
        (ed/editor-view "" "clj")

        focusable
        (.getFlow editor)

        focus-on-editor
        #(fxj/thread
           (Thread/sleep 500)
           ;(println "focus on Input editor")
           (fx/later
             (.requestFocus ^Node focusable)))

        do-history-fn
        (fn [direction global?]
          (hist/do-history editor repl-uuid current-history-index_ direction global?))

        interrupt-button
        (interrupt-button)

        run-button
        (run-button true)

        clear-checkbox
        (doto
          (fx/checkbox "Clear"
                       :tooltip
                       "Clear on 'Run'. Code is cleared after successful evaluation.")
          (.setStyle "-fx-padding: 3px;")
          (.setSelected true))

        do-clear-fn
        (fn [inverse-clear]  ;; do the opposite of clear-checkbox
          (let [clear-checked
                (.isSelected clear-checkbox)
                do-clear
                (if inverse-clear (not clear-checked) clear-checked)]
            (when do-clear
              (fx/later (ed/set-text editor "")))))

        do-eval-fn
        (fn [code-str inverse-clear]
            (history-wrapper
              repl-uuid
              current-history-index_
              code-str
              (fn []
                  (do-eval
                    code-str
                    run-button
                    interrupt-button
                    #(.getText ns-label)
                    update-ns-fn
                    file-name
                    focusable
                    #(do-clear-fn inverse-clear)))))

        on-closed-fn
        #(.fire interrupt-button)

        prev-button
        (doto (fx/button
                (str  \u25C0)  ;; up: \u25B2,  left: \u25C0
                :tooltip
                "Previous local history.         CLICK
Previous global history.  SHIFT-CLICK")
          (.setOnMouseClicked
              (fx/event-handler-2 [_  e]
                (do-history-fn hist/PREV (.isShiftDown ^MouseEvent e))
                (.consume e))))

        next-button
        (doto (fx/button
                (str \u25B6)  ;; down: \u25BC,  right: \u25B6
                :tooltip
                "Next local history.         CLICK
Next global history.  SHIFT-CLICK")
         (.setOnMouseClicked
             (fx/event-handler-2 [_ e]
                (do-history-fn hist/NEXT (.isShiftDown ^MouseEvent e))
                (.consume e))))

         ;structural-combo (ComboBox. (fx/observablearraylist "Paredit" "No structural"))
         ;paredit-kphandler (paredit/key-pressed-handler)
         ;paredit-kthandler (paredit/key-typed-handler)
         ;_ (doto editor
         ;   (.addEventFilter KeyEvent/KEY_PRESSED
         ;                    (fx/event-handler-2 [_ event]
         ;                         (when (-> structural-combo .getSelectionModel (.isSelected 0))
         ;                               (.handle paredit-kphandler event))))
         ;
         ;   (.addEventFilter KeyEvent/KEY_TYPED
         ;     (fx/event-handler-2 [_ event]
         ;                         (when (-> structural-combo .getSelectionModel (.isSelected 0))
         ;                               (.handle paredit-kthandler event)))))
         ;_ (-> structural-combo .getSelectionModel (.select 0))

        top
        (layout/menubar true
          ns-label)

        bottom
        (layout/menubar false
          ns-label
          (fx/region :hgrow :always)
          prev-button
          next-button
          (fx/region :hgrow :sometimes)
          ;structural-combo
          ;(fx/region :hgrow :always)
          clear-checkbox
          ;(fx/region :hgrow :always)
          interrupt-button
          run-button)

        border-pane
        (fx/borderpane
          ;:top top
          :center editor
          :bottom bottom
          :insets [5 0 0 0])

        get-code-fn
        #(ed/text editor)

        key-pressed-handler
        (fx/key-pressed-handler {
                                 #{:SHORTCUT :ENTER}
                                 #(.fire run-button)

                                 #{:SHIFT :SHORTCUT :ENTER}
                                 #(when-not (.isDisabled run-button)
                                    (do-eval-fn (get-code-fn) true))

                                 #{:SHORTCUT :ESCAPE}
                                 #(.fire interrupt-button)})]

    (fx/set-onaction run-button #(do-eval-fn (get-code-fn) false))

    (.addEventFilter border-pane KeyEvent/KEY_PRESSED key-pressed-handler)
    ;; TODO: ensure editor always gets focus back when focus in window ...
    ;; TODO: colorcode also when history is the same
    ;; TODO: nicer tooltips.  (monospace and better colors)

    (add-watch selected_ tab
               #(when (= %4 tab)  (focus-on-editor)))

    (add-watch focused_ tab
               #(when (and %4 (= @selected_ tab))  (focus-on-editor)))

    [border-pane on-closed-fn]))


(defn new-input-tab [selected_ focused_ & {:keys [ns]}]
  (let [nr (hist/next-repl-nr)
        file-name (format "Input %s" nr)
        tab (Tab. file-name)
        [root on-closed-fn] (new-input-root file-name selected_ focused_ tab :ns ns)]

    (reset! selected_ tab)
    (reset! focused_ true)

    (doto tab
      (.setContent root)
      (.setOnClosed (fx/event-handler (on-closed-fn))))))



(defn new-tabbed-input-root [& {:keys [ns]}]
  (let [selected_ (atom nil)
        focused_ (atom false)
        [root ^TabPane tabpane] (layout/tabpane "Inputs" "New Input"
                                                #(new-input-tab selected_ focused_ :ns ns)
                                                false)]
    (layout/set-listeners tabpane selected_ focused_)

    (fxj/thread
      (Thread/sleep 500)
      ;(println "focus on Inputs"
      (fx/later (.requestFocus tabpane)))

    root))


(defn- input-scene [root]
  (doto
    (fx/scene root :size [600 300])
    (fx/add-stylesheets "styles/codearea.css")))


;; For Input stage layout
(defonce ^:private input-vertical-offset (atom 0))
(defonce ^:private input-horizontal-offset (atom 0))
(defn next-input-vertical-offset [] (swap! input-vertical-offset inc))
(defn next-input-horizontal-offset [] (swap! input-horizontal-offset inc))


(defn new-input-stage [& [ns]]
  ;; TODO: consolidate/fix integrations/dependencies
  ;; TODO: add interupt-posibility (button) to/for run-thread

  (fxj/thread (repl/session-ensure! true))
  (let [
        repl-nr
        (hist/next-repl-nr)

        root (new-tabbed-input-root  :ns ns)

        scene ^Scene (input-scene root)

        screen-WH (-> (fx/primary-screen) .getVisualBounds fx/WH)

        horizontal-offset (* ^int (next-input-horizontal-offset) 5)
        vertical-offset (* ^int (next-input-vertical-offset) 20)

        stage
        (fx/now
          (doto (fx/stage
                  :title (format "Input %s" repl-nr)
                  :scene scene
                  :sizetoscene true

                  :location [(- ^double (first screen-WH) (.getWidth scene) 30 horizontal-offset)
                             (+ 80 vertical-offset)])))]

    stage))


;;; DEV ;;;


;(when (env :repl?) (println "WARNING: Running george.application.input/new-input-stage") (new-input-stage))

