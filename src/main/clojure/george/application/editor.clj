;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.editor
  ^{:doc
    "This is not to be confused with george.editor. it replaces code.clj.
    It builds a complete editor node - single, tabbed, or in separate window (single or tabbed).
    It includes file handling - saving and opening, and code interactions such as Load and Eval."}
  (:require
    [clojure.core.async :refer [>!! <! chan timeout sliding-buffer thread go go-loop close!]]
    [george.javafx :as fx]
    [george.application.output :refer [oprint oprintln]]
    [environ.core :refer [env]]
    [george.util.singleton :as singleton]
    [george.application.ui.layout :as layout]
    [george.editor.core :as ed]
    [george.javafx.java :as fxj]
    [george.util.file :as guf]
    [george.application.input :as input]
    [george.application.launcher :as appl]
    [george.application.file :as gf])

  (:import
    [javafx.scene.control Tab TabPane]
    [javafx.scene.input KeyEvent]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(declare
  save
  close)


(defn- save-to-swap-channel []
  (let [c (chan (sliding-buffer 1))]  ;; we only need to run latest queued save-fn
    (go-loop []
      (<! (timeout 5000))  ;; wait 5 seconds before running next save-fn
      (let [f (<! c)]
        (when (and f (not= :halt f))
          (f)
          (recur))))
    c))


(defn save-to-swap
  "Returns the content that was saved.
  Does the actual save-to-swap - both for 'queue-save-to-swap' and before eval/run.
  If no f#
    then if f
      then make f#, set it on info and write to it.
    else make ISO#, set it on info and write to it."
  [editor file-info_]
  (let [{:keys [file swap-file]} @file-info_
        f (if swap-file swap-file
                        (if file (gf/create-swap file)
                                 (gf/create-temp-swap)))
        content (ed/text editor)]
    (spit f content)
    (swap! file-info_ assoc :swap-saved? true :saved? false :swap-file f)
    content))


(defn save-to-swap-maybe [editor file-info_]
  (when-not (:swap-saved? @file-info_)
    (save-to-swap editor file-info_)))


(defn queue-save-to-swap
  "Saves to f#.
  If f# exists
    then rewrite to existing f#
    else if f# exists (but not f#)
      then create f# and write to it
      else create ISO# (in default document dir) and write to it.
  "
  [editor file-info_ save-chan]
  (>!! save-chan #(save-to-swap-maybe editor file-info_)))


(defn save-as
  "Popup save-dialog.
  When new f returned from dialog
     then if f# exists
        then rename to new f
        else write content new f directly.
  Returns f, if saved, else nil.
  "
  [editor file-info_]
  (when-let [f (gf/create-file-for-save (appl/current-application-stage))]
    (swap! file-info_ assoc :file f)
    (save editor file-info_)))


(defn save
  "If f
    then rename f# to f.  (making f# disappear and f be overwritten by f#)
    else (no f)  switch to save-as.
  Returns f if save was ok, else nil.
  "
  [editor file-info_]
  (save-to-swap-maybe editor file-info_)
  (let [{:keys [saved? file swap-file]} @file-info_]
    (if saved?
      file
      (if-not file
        (save-as editor file-info_)
        (do
          (gf/save-swap swap-file file)
          (swap! file-info_ assoc :saved? true :swap-file nil)
          file)))))


(defn open
  "If selected file is f#, the place in f# and open content, leaving it up to the user whether they want to save it (later) to the f.  If the f# has a matching f, then set that as potential f, else (ISO#), set f to nil."
  [editor file-info_]
  (when (close editor file-info_)
    (when-let [f (gf/select-file-for-open (appl/current-application-stage))]
      (let [content (slurp f)
            ;; If the user opens a swap-file, it will be used as swap-file, and file is set to nil
            swapf? (gf/swap? (.getName f))
            [swapf f] (if swapf? [f nil] [nil f])]
        (swap! file-info_ assoc :file f :swap-file swapf  :saved? (boolean f) :swap-saved? true
               :ignore-next-buffer-change true)  ;; Signals the state-listener to ignore the next text change.
        (ed/set-text editor content)))))


(def alert-header
  "You have unsaved changes.\nIf you don't save, your edits will be lost.")

(def alert-message
  "   'Save'            will save and close.
   'Don't save'  will close without saving.
   'Cancel'        will not close the editor.
")


(defn clean-and-clear [file-info_]
  (when-let [f (:swap-file @file-info_)]
    (guf/delete-file f))
  (swap! file-info_ assoc :swap-file nil :file nil :saved? true :swap-saved? true)
  true)


(defn close
  "If f# and f different, ask user if they want to swap.
   If no, then delete f#
  If ISO# (and no f), ask user if they want to save.
  (f no, then delete ISO#, else switch to save-as
  Returns true, if close is acceptable, else false (if close should be interrupted)."
  [editor file-info_]
  (save-to-swap-maybe editor file-info_)
  (if (:saved? @file-info_)
    true
    (let [res (fx/alert alert-message
                        :title "Save?"
                        :header alert-header
                        :owner (appl/current-application-stage)
                        :options ["Save" "Don't save"]
                        :cancel-option? true
                        :type :confirmation)]
      (case res
        0 (if (save editor file-info_)
              (clean-and-clear file-info_)
              (close editor file-info_))
        1 (clean-and-clear file-info_)
        ;; default
        false))))


(defn on-close
  "Wraps 'close', handling event correctly."
  [editor file-info_ event]
  (when-not (close editor file-info_)
            (.consume event)))


(defn state-listener [editor tab file-info_ save-chan]
  (let [state_ (.getStateAtom editor)]
    (add-watch state_ tab
               (fn [_ _ {pbuffer :buffer} {buffer :buffer}]
                 (when-not (identical? pbuffer buffer) ;; Comparing the buffers' identity is fastest
                   (if (:ignore-next-buffer-change @file-info_)
                     (swap! file-info_ dissoc :ignore-next-buffer-change)  ;; Got the signal  Now removing it.
                     (do (swap! file-info_ assoc :swap-saved? false :saved? false)
                         (queue-save-to-swap editor file-info_ save-chan))))))))


(defn- indication
  "Assembles the string shown in the editor tab.
  Filename or '<no file>.  Appends '*' if not saved to named file.  Appends '#' if content not yet saved to swap-file."
  [{:keys [file swap-saved? saved?]}]
  (let [fname (if file (.getName file) "<no file>")]
    (format "%s %s%s " fname (if-not saved? "*" "") (if-not swap-saved? "#" ""))))


(defn indicator
  "Updates the file-name and status in the tab whenever file-info_ changes."
  [text-prop file-info_]
  (.set text-prop (indication @file-info_))
  (add-watch file-info_ :indicator
             (fn [_ _ _ file-info]
               (fx/later (.set text-prop (indication file-info))))))


(defn new-editor-root [selected_ focused_ tab & {:keys [ns] :or {ns "user"}}]
  (let [
        file-info_
        (atom {:file nil
               :swap-file nil
               :swap-saved? true  ;; saved to swap-file? Set to false by state-listener, and then true by auto-save
               :saved? true}) ;; swapped to real file? Set to false by auto-save and then to true by save/save-as

        save-chan (save-to-swap-channel)

        run-button
        (input/run-button)

        interrupt-button
        (input/interrupt-button)

        ns-label
        (input/ns-label)
        update-ns-fn
        (input/set-ns-label-fn ns-label)
        _ (update-ns-fn (or ns "user"))

        editor
        (doto (ed/editor-view "" :clj)
              (state-listener tab file-info_ save-chan))

        focusable
        (.getFlow editor)

        focus-on-editor
        #(fxj/thread
           (Thread/sleep 500)
           ;(println "focus on Editor editor")
           (fx/later
             (.requestFocus focusable)))

        do-eval-fn
        (fn []
          (save-to-swap-maybe editor file-info_)
          (input/do-eval
                (ed/text editor)
                run-button
                interrupt-button
                #(.getText ns-label)
                update-ns-fn
                (if-let [f (:file @file-info_)] (.getName f) "<no file>")
                focusable
                nil))

        oncloserequest-fn
        #(on-close editor file-info_ %)

        onclosed-fn
        (fn []
          (.fire interrupt-button)  ;; stop any running evaluations
          (remove-watch (.getStateAtom editor) tab)  ;; clean up after editor-state-listener
          (>!! save-chan :halt) ;; stop save-channel loop
          (close! save-chan))

        open-fn
        #(open editor file-info_)
        save-fn
        #(save editor file-info_)
        save-as-fn
        #(save-as editor file-info_)

        close-fn
        #(layout/close-tab-nicely nil tab)

        top-menubar
        (layout/menubar true
          (layout/menu
            [:button "File" :bottom [[:item "Open ..." open-fn]
                                     [:separator]
                                     [:item "Save" save-fn]
                                     [:item "Save as ..." save-as-fn]
                                     [:separator]
                                     [:item "Close" close-fn]]]))

        bottom-menubar
        (layout/menubar false
          ns-label
          (fx/region :hgrow :always)
          interrupt-button
          run-button)

        root
        (fx/borderpane
          :top top-menubar
          :center editor
          :bottom bottom-menubar)]

    (add-watch selected_ tab
               #(when (= %4 tab)  (focus-on-editor)))
    (add-watch focused_ tab
               #(when (and %4 (= @selected_ tab))  (focus-on-editor)))

    (fx/set-onaction run-button #(do-eval-fn))

    (indicator (.textProperty tab) file-info_)

    (doto root
      (.addEventFilter KeyEvent/KEY_PRESSED
                       (fx/key-pressed-handler {#{:O :SHORTCUT}        open-fn
                                                #{:S :SHORTCUT}        save-fn
                                                #{:S :SHORTCUT :SHIFT} save-as-fn
                                                #{:E :SHORTCUT}        #(println "Eval ... NOT")
                                                #{:ENTER :SHORTCUT}    #(.fire run-button)
                                                #{:ESCAPE :SHORTCUT}   #(.fire interrupt-button)})))

    [root oncloserequest-fn onclosed-fn]))


(defn new-editor-tab [selected_ focused_ & {:keys [ns]}]
  (let [tab  (Tab.)
        [root oncloserequest-fn onclosed-fn] (new-editor-root selected_ focused_ tab :ns ns)]

    (reset! selected_ tab)
    (reset! focused_ true)

    (doto tab
      (.setContent root)
      (.setOnCloseRequest (fx/event-handler-2 [_ e] (oncloserequest-fn e)))
      (.setOnClosed (fx/event-handler (onclosed-fn))))))


(defn new-tabbed-editor-root [& {:keys [ns with-one?]}]

  (let [selected_ (atom nil)
        focused_ (atom false)

        [root ^TabPane tabpane]
        (layout/tabpane "Editors" "New editor"
                        #(new-editor-tab selected_ focused_ :ns ns)
                        with-one?)]

    (layout/set-listeners tabpane selected_ focused_)

    root))


(defn create-stage [& [with-one?]]
  (fx/now
    (fx/stage
      :title "Editor stage"
      :oncloserequest  #(singleton/remove ::editor-stage)
      :tofront true
      :alwaysontop true
      :sizetoscene false
      :scene (fx/scene (new-tabbed-editor-root :with-one? with-one? :ns "user.turtle"))
      :size [600 400])))


(defn get-or-create-stage []
  (singleton/get-or-create ::editor-stage create-stage))



;;; DEV ;;;

;(when (env :repl?) (println "WARNING: Running george/create-stage" (create-stage true)))

