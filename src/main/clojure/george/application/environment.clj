;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  george.application.environment
  (:require
    [clojure.string :as cs]
    [george.javafx :as fx]
    [george.application.turtle.turtle :as tr]
    [george.application
     [input :as input]
     [output-input :as oi]]
    [george.util.singleton :as singleton]
    [george.application.code :as code]
    [george.javafx.java :as fxj]
    [george.application.launcher :as launcher]
    [george.application.ui.layout :as layout]
    [george.application.ui.styled :as styled])
  (:import (javafx.scene Node)
           (javafx.scene.layout Pane)
           (javafx.scene.control SplitPane)
           (javafx.geometry Orientation)))


(def ide-types #{:ide :turtle})


(defn- doc-str [var]
  (let [m (meta var)
        n (str (:name m))
        dc (:doc m)
        argls (:arglists m)
        combos (cs/join "  "
                        (map #(str "("
                                   n
                                   (if (empty? %) ""  (str " " (cs/join " " %)))
                                   ")")
                             argls))]
    (str combos \newline \newline dc)))


(defn turtle-commands-root []
  (let [
        commands tr/ordered-command-list
        name-fn #(-> % meta :name str)
        doc-fn doc-str
        labels
        (map #(doto (fx/label (name-fn %)) (fx/set-tooltip (doc-fn %)))
              commands)]

    (fx/scrollpane
      (apply fx/vbox
             (concat (fxj/vargs-t* Node labels)
                     [:padding 10 :spacing 5])))))


(defn- turtle-commands-stage-create []
  (let [
        stage-WH [200 200]
        screen-WH (-> (fx/primary-screen) .getVisualBounds fx/WH)]
       (fx/now
         (fx/stage
           :title "Turtle commands"
           :location [(- (first screen-WH) (first stage-WH) 10)
                      (- (second screen-WH) (second stage-WH) 10)]
           :sizetoscene false
           :tofront true
           :onhidden #(singleton/remove ::commands-stage)
           :size stage-WH
           :resizable false
           :scene (fx/scene (turtle-commands-root))))))



(defn- turtle-commands-stage []
  (singleton/get-or-create
    ::commands-stage turtle-commands-stage-create))


(defn- toolbar-pane [is-turtle]

   (let [
         ;button-width
         ;150

         ;user-ns-str
         ;(if is-turtle "user.turtle" "user")

         ;pane
         ;(fx/hbox
         ;  (fx/button "Code"
         ;             :width button-width
         ;             :onaction #(code/new-code-stage :namespace user-ns-str)
         ;             :tooltip "Open a new code editor")

         pane
         (fx/hbox
           (styled/heading "Turtle Geometry IDE" :size 20)
           :spacing 10
           :padding 10)]
     pane))



(def xy [(+ (launcher/xyxy 2) 5) (launcher/xyxy 1)])


(defn- create-toolbar-stage [ide-type]
  (let [is-turtle (= ide-type :turtle)]
    (fx/now
      (fx/stage
        :location xy
        :title (if is-turtle "Turtle Geometry" "IDE")
        :scene (fx/scene (toolbar-pane is-turtle))
        :sizetoscene true
        :resizable false
        :onhidden #(singleton/remove [::toolbar-stage ide-type])))))



(defn toolbar-stage [ide-type]
  (doto
    (singleton/get-or-create [::toolbar-stage ide-type]
                             #(create-toolbar-stage ide-type))
    (.toFront)))



(defn- main-root
  "A horizontal split-view dividing the main area in two."
  [ide-type]
  (let [
        user-ns-str
        (if (= ide-type :turtle) "user.turtle" "user")

        left
        (layout/tabpane "Editors" "New editor" #(code/new-code-tab :namespace user-ns-str) false)

        oi-root
        (oi/output-input-root)

        right
        (doto
          (SplitPane. (fxj/vargs-t Node (turtle-commands-root) oi-root))
          (.setOrientation Orientation/VERTICAL))

        root
        (doto
          (SplitPane. (fxj/vargs-t Node left right))
          (.setDividerPosition 0 0.6))]

    ;; TODO: Implement general ratio function for height of total height
    ;; TODO: Calculate height based on application stage or passed-in dim.
    (.setDividerPosition right 0 0.2)
    (.setDividerPosition oi-root 0 0.6)
    ;; TODO: Ensure input gets focus!
    ;; TODO: Nicer SplitPane dividers!  See
    root))


(defn- ide-root-create [ide-type]
  (assert (ide-types ide-type))
  (let [[root master-setter detail-setter] (layout/master-detail true)]
    (master-setter (toolbar-pane ide-type))
    (detail-setter (main-root ide-type))
    root))


(defn ide-root [ide-type]
  (singleton/get-or-create [::ide-root ide-type]
                           #(ide-root-create ide-type)))


(defn ide-root-dispose [ide-type]
  (assert (ide-types ide-type))
  (singleton/remove [::ide-root ide-type]))

;;;; main ;;;;


(defn -main
  "Launches an input-stage as a stand-alone application."
  [& args]
  (let [ide-type (first args)]
    (assert (ide-types ide-type))
    (fx/later (toolbar-stage ide-type))
    (fx/text "ide/main called")))

;;; DEV ;;;

;(println "WARNING: Running george.application.turtle.environment/-main" (-main :turtle))
