;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.output-input
  (:require
    [environ.core :refer [env]]
    [george.application
     [output :as output]
     [input :as input]
     [launcher :as launcher]]
    [george.util.singleton :as singleton]
    [george.javafx :as fx]
    [george.javafx.java :as fxj])
  (:import
    [javafx.scene.control SplitPane]
    [javafx.geometry Orientation]
    [javafx.scene Node]))


(def OIS_KW ::output-input-stage)


(defn output-input-root [& {:keys [ns]}]
  (let [[o-root clear-button] (output/output-root)
        inputs-root (input/new-tabbed-input-root :ns ns)
        split-pane
        (doto (SplitPane. (fxj/vargs-t Node o-root inputs-root))
          (.setOrientation Orientation/VERTICAL))]

    (.fire clear-button)

    split-pane))


(def xy [(+ (launcher/xyxy 2) 5) 95])
(def wh [800 (- (launcher/xyxy 3) (xy 1))])


(defn- create-stage []
  (fx/now
    (let [oi-root (output-input-root)

          stage
          (fx/stage
            :title " Input-Output"
            :location xy
            :size wh
            :sizetoscene false
            :scene (doto (fx/scene oi-root)
                     (fx/add-stylesheets "styles/codearea.css"))

            :onhidden #(do (output/teardown-output)
                           (singleton/remove OIS_KW)))

          ;; Calculate the exact position for divider
          in-h 200
          h (wh 1)
          out-h (- h in-h)
          div-pos (/ out-h h)]
      (.setDividerPosition oi-root 0 div-pos)               ;; must be called after stage is created
      stage)))


(defn show-or-create-stage []
  ;(println "oi/show-or-create-stage")
  (doto
    (singleton/get-or-create OIS_KW create-stage)
    (.toFront)))


(defn -main [& _]
  (fx/later
    (show-or-create-stage)))


;;;;


;(when (env :repl?) (println "george.application.output-input/-main") (-main))