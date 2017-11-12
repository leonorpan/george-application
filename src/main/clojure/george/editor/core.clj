;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.editor.core
  (:require [george.javafx :as fx]
            [george.util.java :as j]
            [george.editor.buffer :as b]
            [george.editor.state :as st]
            [george.editor.view :as v]
            [george.editor.input :as i])

  (:import (org.fxmisc.flowless VirtualFlow VirtualizedScrollPane)
           (javafx.collections FXCollections ObservableList)
           (javafx.scene.input KeyEvent)
           (java.util List)))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defn- new-flow
  "Takes an observable list (as a device for updating its content) and a 1-arg function (the arg is an item from the list), which, when called, will return an instance of org.fxmisc.flowless.Cell.
  Returns a new (vertical) org.fxmisc.flowless.VirtualFlow"
  [^ObservableList items cell-factory-fn]
  (VirtualFlow/createVertical items (j/function cell-factory-fn)))


(defn- editor [s]
  (let [
        [buf nl-str]
        (b/new-buffer s)

        lines-list (FXCollections/observableArrayList ^List (b/split-buffer-lines buf))

        state_ (atom (st/new-state buf nl-str lines-list))
        state-derived_ (st/new-state-derived state_)
        line-count_ (st/new-line-count state-derived_)

        graphic-offset_  (atom 0.0)

        flow ^VirtualFlow
        (new-flow (st/observable-list state_)
                  (partial v/new-paragraph-cell state-derived_ graphic-offset_ line-count_))

        _ (add-watch state-derived_ (Object.) (fn [_ _ _ derived]
                                                  (v/ensure-caret-visible flow derived)))


        ;;  Needs to get some information from 'flow'
        ;; (and from clicked-in 'cell') before determining appropriate action.
        mouse-event-handler
        (i/mouse-event-handler flow (partial st/mouseaction state_))]


    (doto flow

      ;; Important! Otherwise the flow can not receive events.
      (.setFocusTraversable true)

      (-> .breadthOffsetProperty
          (.addListener (fx/changelistener [_ _ _ offset]
                                           (reset! graphic-offset_ offset))))

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
            (fx/changelistener [_ _ prev-width width]
                               ;; to re-layout so as to ensure-visible on caret after flow has been made visible.
                               (when (and (zero? ^double prev-width) (pos? ^double width))
                                 (swap! state-derived_ assoc :triggering-hack (Object.))
                                 (reset! line-count_ (:line-count @state-derived_)))))))

    [flow state_]))



(definterface IEditorPane
  (getState []))



(defn editor-view
 ([]
  (editor-view ""))
 ([s]
  (let [[flow state_] (editor s)]
    (proxy [VirtualizedScrollPane IEditorPane] [flow]
      (getState [] state_)))))


