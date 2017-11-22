;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.editor.core
  (:require
    [clojure.pprint :refer [pprint]]
    [george.javafx :as fx]
    [george.util.java :as j]
    [george.editor.buffer :as b]
    [george.editor.state :as st]
    [george.editor.view :as v]
    [george.editor.input :as i]
    [george.editor.formatters.parinfer :as parinfer] :reload)
  (:import (org.fxmisc.flowless VirtualFlow VirtualizedScrollPane)
           (javafx.scene.input KeyEvent)))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defn- editor
 ([s typ]
  (let [
        [buf nl-str] (b/new-buffer s)
        typ (keyword typ)
        formatter (when (= typ :clj) (parinfer/new-formatter))

        state_ (st/new-state-atom buf nl-str typ formatter)

        scroll-offset_  (atom 0.0)

        flow
        (VirtualFlow/createVertical
          (st/observable-list state_)
          (j/function
            (partial v/new-paragraph-cell state_ scroll-offset_)))
        
        ;;  Needs to get some information from 'flow'
        ;; (and from clicked-in 'cell') before determining appropriate action.
        mouse-event-handler
        (i/mouse-event-handler flow (partial st/mouseaction state_))]
  
    (add-watch state_ :ensure-caret-visible
               (fn [_ _ _ state]
                 (v/ensure-caret-visible flow state)))

    (doto flow

      ;; Important! Otherwise the flow can not receive events.
      (.setFocusTraversable true)

      (-> .breadthOffsetProperty
          (.addListener (fx/changelistener [_ _ _ offset]
                                           (reset! scroll-offset_ offset))))

      (.addEventHandler KeyEvent/ANY (i/key-event-handler
                                       (partial st/keypressed state_)
                                       (partial st/keytyped state_)))

      (.setOnMousePressed mouse-event-handler)
      (.setOnMouseDragged mouse-event-handler)
      (.setOnMouseDragOver mouse-event-handler)
      (.setOnMouseReleased mouse-event-handler)
      (.setOnMouseDragReleased mouse-event-handler)

      (-> .widthProperty
          (.addListener
            (fx/changelistener [_ _ prev-w w]
                               ;; to re-layout so as to ensure-visible on caret after flow has been made visible.
                               (when (and (zero? ^double prev-w) (pos? ^double w))
                                 (swap! state_ assoc :triggering-hack :hacked))))))


    [flow state_])))


(definterface IEditorPane
  (getStateAtom []))


(defn editor-view
 "Returns a subclass of VirtualizedScrollPane.

 No args, or 'content-string' and optional 'content-type'.

 'content-type' can be 'nil' (for plain text)
 or (currently) one of: :clj or \"clj\".

 'content-type' will effect formatting and coloring."

 ([]
  (editor-view "" nil))
 ([^String content-string & [content-type]]
  (let [[flow state_] (editor content-string content-type)]
    (proxy [VirtualizedScrollPane IEditorPane] [flow]
      (getStateAtom [] state_)))))

