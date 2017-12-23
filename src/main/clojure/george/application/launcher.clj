;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.


(ns george.application.launcher

  (require
    [clojure.repl :refer [doc]]
    [clojure.java.browse :refer [browse-url]]
    [george.javafx :as fx]
    [george.application.app-loader :as app-loader]
    [george.util.singleton :as singleton]
    [george.application.ui.stage :as ui-stage]
    [george.application.repl-server :as repl-server]
    [clojure.java.io :as cio]
    [environ.core :refer [env]]
    [g])
  (:import [javafx.scene.image ImageView Image]
           [javafx.scene.paint Color]
           [javafx.geometry Rectangle2D]
           [javafx.stage Stage]
           [javafx.application Platform]
           (javafx.scene.control Hyperlink)
           (javafx.beans.property SimpleDoubleProperty)
           (javafx.scene.layout Pane VBox)))

;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)

(def about-tmpl "
George version: %s
Clojure version: %s
Java version: %s


Copyright 2015-2017 Terje Dahl.
Powered by open source software.
")

(def ABOUT_STAGE_KW ::about-stage)


(defn- about-stage-create []
  (let [text
        (fx/label
          (format about-tmpl
             (slurp (cio/resource "george-version.txt"))
             (clojure-version)
             (env :java-version)))
        link
        (doto (Hyperlink. "www.george.andante.no")
          (.setStyle "-fx-border-color: transparent;-fx-padding: 10 0 10 0;-fx-text-fill:#337ab7;")
          (.setOnAction (fx/event-handler (browse-url "http://www.george.andante.no"))))]
      (fx/stage
         :style :utility
         :sizetoscene true
         :title "About George"
         :onhidden #(singleton/remove ABOUT_STAGE_KW)
         :scene (fx/scene
                  (fx/vbox
                    (ImageView. (Image. "graphics/George_logo.png"))
                    text
                    link
                    :padding 10
                    :background (fx/color-background Color/WHITE))))))


(defn- about-stage []
  (if-let [st ^Stage (singleton/get ABOUT_STAGE_KW)]
    (.hide st)
    (singleton/get-or-create ABOUT_STAGE_KW about-stage-create)))


(def tile-width 64)

(def launcher-width (+ ^int tile-width 20))

(def visual-bounds (.getVisualBounds (fx/primary-screen)))

(def xyxy
  (let [vb ^Rectangle2D visual-bounds]
    [(.getMinX vb)
     (.getMinY vb)
     (+ (.getMinX vb) ^int launcher-width)
     (.getMaxY vb)]))

(def launcher-height (- ^int (xyxy 3) ^int (xyxy 1)))


(defn launcher-root-node []
  (let [

        george-icon
        (doto (ImageView. (Image. "graphics/George_icon_128_round.png"))
          (.setFitWidth tile-width)
          (.setFitHeight tile-width))

        about-label
        (doto
          (fx/label "About")
          (.setStyle "-fx-font-size: 10px;")
          (.setOnMouseClicked (fx/event-handler (about-stage))))

        app-infos
        (app-loader/load-apps)

        app-tiles-and-paddings
        (flatten
          (map #(vector
                  (app-loader/padding 30)
                  (app-loader/launcher-app-tile % tile-width))
               app-infos))

        root ^VBox
        (apply fx/vbox
               (concat
                 [
                  (app-loader/padding 10)
                  george-icon
                  (app-loader/padding 10)
                  (app-loader/hr launcher-width)]

                 app-tiles-and-paddings

                 [
                  (fx/region :vgrow :always)

                  (app-loader/hr launcher-width)
                  (app-loader/padding 5)
                  about-label
                  (app-loader/padding 5)
                  :padding 5
                  :alignment fx/Pos_TOP_CENTER]))]

    (doto root
         (.setMaxWidth launcher-width)
         (.setMaxHeight launcher-height))))



(defn- launcher-close-handler [^Stage launcher-stage]
  (fx/event-handler-2 [_ e]
     (.toFront launcher-stage)
     (let [repl? (boolean (env :repl?))
           button-index
           (fx/now
             (fx/alert
               (str "Do you want to quit George?"
                    (when repl? "\n\n(You are running from a repl.\n'Quit' will not exit the JVM instance.)"))
               :title "Quit?"
               :options [(str "Quit")]
               :owner launcher-stage
               :mode nil
               :cancel-option? true))
           exit? (= 0 button-index)]

          (if exit?
            (do (repl-server/stop!)
                (println "Bye for now!" (when repl? " ... NOT"))
                (Thread/sleep 300)
                (when-not repl?
                  (fx/now (Platform/exit))
                  (System/exit 0)))
            (.consume e))))) ;; do nothing


(defn- double-property [init-value value-change-fn]
  (doto (SimpleDoubleProperty. init-value)
    (.addListener
      (fx/changelistener
        [_ _ _ new-val]
        (value-change-fn new-val)))))


(defn- morphe-launcher-stage [^Stage stage ^Pane launcher-root]
  ;; Fade out old content.
  (fx/later
    (doto stage
          (.toFront)
          (.setTitle  "...")))

  (ui-stage/swap-with-fades stage (fx/borderpane) true 500)
  (let [
        target-x (xyxy 0)
        target-y (xyxy 1)
        target-w launcher-width
        target-h launcher-height

        x-prop (double-property (.getX stage) #(.setX stage %))
        y-prop (double-property (.getY stage) #(.setY stage %))
        w-prop (double-property (.getWidth stage) #(.setWidth stage %))
        h-prop (double-property (.getHeight stage) #(.setHeight stage %))]
    ;; Transition stage.
    (fx/synced-keyframe
      300
      [x-prop target-x]
      [y-prop target-y]
      [w-prop target-w]
      [h-prop target-h])
    ;; Fade in Launcher root
    (ui-stage/swap-with-fades stage launcher-root true 1000)

    (.setOnKeyPressed (.getScene stage)
                      (fx/key-pressed-handler
                        {#{:ALT :Q}
                         #(do
                            (repl-server/stop!)
                            (.hide stage))}))

    (fx/later
         ;; TODO: prevent fullscreen.  Where does the window go after fullscreen?!?
        (doto stage
          (.setTitle "George")
          (.setResizable false)
          (fx/setoncloserequest (launcher-close-handler stage))))))


;; also called from Main
(defn starting-stage [& [^Stage stage]]
  (if stage
    (fx/now
      (doto stage
        (.setTitle "Loading ...")
        (.setScene (fx/scene (ui-stage/scene-root-with-child)
                             :size [240 80]))
        (.centerOnScreen)
        (.show)
        (.toFront)))

    (fx/now
      (fx/stage :title "Loading ..."
                :scene (fx/scene (ui-stage/scene-root-with-child)
                                 :size [240 80])
                :tofront true))))


;; called from Main
(defn start
  "Three versions of this method allow for different startup-strategies. The result is always that a created or given stage will be transformed (animated) into the launcher stage."
  ([]
   (start (starting-stage)))
  ([stage]
   (start stage (launcher-root-node)))

  ([stage root-node]
   (morphe-launcher-stage stage root-node)))


(defn -main
  "Launches George (launcher) as a stand-alone application."
  [& args]
  (println "george.application.launcher/-main"
           (if (empty? args)
             ""
             (str " args: " (apply str (interpose " " args)))))
  (start))


;;; DEV ;;;

;(when (env :repl?)  (-main))
;(when (env :repl?)  (start))
;(when (env :repl?)  (start (starting-stage)))

;; TODO: sort out laucher-code/app-loader
;; TODO: sort out sizes for app-tiles et al
