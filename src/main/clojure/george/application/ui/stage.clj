;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.ui.stage
  "Namespace contains utilities for creating and working with stages"
  (:require
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.application.ui.styled :as styled]))


(defn scene-root-with-child []
  (fx/stackpane (styled/new-heading "Starting Launcher ...")))


(defn swap-stage-ensure
  "Given a stage, ensures that is is a 'swap-stage' - i.e. one that allows for swap-with-fades"
  [stage]
  "A swap-parent is stackpane which can be used by swap-with-fade in a scene."
  (if (-> stage .getUserData :swap-parent)
    stage
    (let [parent (scene-root-with-child)]
      (if-let [scene (.getScene stage)]
        (do
          (-> parent .getChildren (.setAll (fxj/vargs (.getRoot scene))))
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