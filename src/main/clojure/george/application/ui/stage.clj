;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.


(ns george.application.ui.stage
  "Namespace contains utilities for creating and working with stages"
  (:require [george.javafx :as fx]))
  ;          [george.javafx.java :as fxj])
  ;(:import (javafx.scene.paint Color)))


;;;; Playing with transitioning content in shared stage.

;;;; Version 1


;(defn- simple-scene-transition []
;  (let [
;        ;; A window root pane, the functionality/view application.
;        root1 (fx/borderpane :center (fx/rectangle :fill Color/RED))
;        ;; Roots will be added and removed from this, and animiated.
;        parent (fx/group root1)
;        scene (fx/scene parent :size [200 200])
;        stage (fx/now (fx/stage :scene scene :tofront true))]
;
;    ;; Now sitch roots
;    (let [parent (-> stage .getScene .getRoot)
;          children (.getChildren parent)
;          new-root (fx/stackpane (fx/rectangle :fill Color/BLUE))]
;      (fxj/thread
;        (println "  ## children:" children)
;        (println "  ## child-count:" (count children))
;        (Thread/sleep 2000)
;
;        (when-not (.isEmpty children)
;          (println "  ## not empty")
;          (let [old-root (.get children 0)]
;            (fx/synced-keyframe 300 [(.opacityProperty old-root) 0.0])
;            (println "fade done on old-root")
;            (fx/later (.remove children old-root))))
;
;        (println "  ## child-count:" (count children))
;        (println "  ## adding new-root")
;        (.setOpacity new-root 0)
;        (fx/later (.add children new-root))
;        (fx/synced-keyframe 2000 [(.opacityProperty new-root) 1.0])
;        (println "  ## child-count:" (count children))))))


;;;; Version 2

;(defn- transition-roots
;  "Inelegant, but it works"
;  [stage new-root]
;  (let [scene-WH (-> stage .getScene fx/WH)]
;    (when-let [scene-0 (.getScene stage)]
;      (let [old-root (.getRoot scene-0)
;            fade-group-1 (fx/group old-root)
;            scene1 (fx/scene (fx/borderpane :center fade-group-1)
;                             :size scene-WH)]
;        (fx/now (.setScene stage scene1))
;        (fx/synced-keyframe 300 [(.opacityProperty fade-group-1) 0.0])))
;    (let [fade-group-2 (doto (fx/group new-root) (.setOpacity 0.0))
;          scene-2 (fx/scene (fx/borderpane :center fade-group-2)
;                            :size scene-WH)]
;      (fx/now (.setScene stage scene-2))
;      (fx/synced-keyframe 1000 [(.opacityProperty fade-group-2) 1.0])
;      ;(fx/now (.setScene stage (fx/scene new-root :size scene-WH)))
;      (println "  ## DID IT!"))))


;(defn- simple-scene-transition []
;  (let [
;        ;; A window root pane, the functionality/view application.
;        root1 (fx/borderpane :center (fx/rectangle :fill Color/GREEN))
;        ;; Roots will be added and removed from this, and animiated.
;        scene (fx/scene root1 :size [200 200])
;        stage (fx/now (fx/stage :scene scene :tofront true))]
;
;    (Thread/sleep 2000)
;    (fxj/thread
;      (transition-roots stage (fx/stackpane (fx/rectangle :fill Color/BROWN))))))


;;;; Version 3


(defn swap-stage-ensure
  "Given a stage, ensures that is is a 'swap-stage' - i.e. one that allows for swap-with-fades"
  [stage]
  "A swap-parent is stackpane which can be used by swap-with-fade in a scene."
  (if (-> stage .getUserData :swap-parent)
    stage
    (let [parent (fx/stackpane)]
      (if-let [scene (.getScene stage)]
        (do
          (.add (.getChildren parent) (.getRoot scene))
          (.setRoot scene parent))
        (.setScene stage (fx/scene parent)))
      (.setUserData stage {:swap-parent parent})
      stage)))


(defn swap-with-fades
  "'stage' may contain ':swap-parent' in its user-data. If not, one will be inserted.
  'new-root' is any type of Node.
  'crossfade?' defaults to false, i.e. fade out old-root and remove, then fade in new-root.
  'duration' is total duration in millis for either type of fade."
  [stage new-root & [crossfade? duration]]
  ;(println "/swap-with-fades " "crossfade?:" crossfade? "duration" duration)
  (let [stage (swap-stage-ensure stage)
        parent (-> stage .getUserData :swap-parent)
        duration (if duration duration 1500)
        children (.getChildren parent)
        old-root (.get children 0)]
    (.setOpacity new-root 0)
    (if crossfade?
      (do
        (fx/now (.add children 0 new-root))
        (fx/synced-keyframe duration
                            [(.opacityProperty new-root) 1.0]
                            [(.opacityProperty old-root) 0.0])
        (fx/later (.remove children old-root)))
      (do
        (fx/synced-keyframe (* duration 3/12)
                            [(.opacityProperty old-root) 0.0])
        (fx/now (.add children 0 new-root))
        (Thread/sleep (* duration 1/12))
        (fx/synced-keyframe (* duration 8/12)
                            [(.opacityProperty new-root) 1.0])
        (fx/later (.remove children old-root))))
    stage))



;(defn- simple-scene-transition []
;  (let [
;        ;; A window root pane, the functionality/view application.
;        root1 (fx/borderpane :center (fx/rectangle :fill Color/RED))
;        ;; Roots will be added and removed from this, and animiated.
;        parent (fx/stackpane root1)
;        scene (fx/scene parent :size [200 200])
;        stage (fx/now
;                (-> (fx/stage :scene scene :tofront true)
;                    (swap-stage-ensure)))]
;    ;; Now switch roots
;    (let [new-root (fx/stackpane (fx/rectangle :fill Color/BLUE))]
;      (swap-with-fades stage new-root false 1500))))


;(simple-scene-transition)