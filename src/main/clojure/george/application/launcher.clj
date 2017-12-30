;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.launcher
  (:require
    [clojure.repl :refer [doc]]
    [clojure.java
     [io :as cio]
     [browse :refer [browse-url]]]

    [environ.core :refer [env]]

    [george
     [javafx :as fx]
     [applet :as applet]]

    [george.javafx.java :as fxj]

    [george.application.ui
     [stage :as ui-stage]
     [layout :as layout]
     [styled :as styled :refer [hr padding]]]

    [george.util.singleton :as singleton]
    [george.application.repl-server :as repl-server]

    [g])

  (:import
    [javafx.scene.image ImageView Image]
    [javafx.scene.paint Color]
    [javafx.geometry Rectangle2D]
    [javafx.stage Stage]
    [javafx.application Platform]
    [javafx.scene.control Hyperlink Button MenuItem ContextMenu]
    [javafx.beans.property SimpleDoubleProperty]
    [javafx.scene.layout Pane VBox]
    [javafx.scene.text TextAlignment]))


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
              (.setStyle "-fx-border-color: transparent; -fx-padding: 10 0 10 0; -fx-text-fill:#337ab7;")
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
                    :background fx/WHITE)))))


(defn- about-stage []
  (if-let [st ^Stage (singleton/get ABOUT_STAGE_KW)]
    (.hide st)
    (singleton/get-or-create ABOUT_STAGE_KW about-stage-create)))


(def tile-width 48)

(def launcher-width (+ ^int tile-width 20))

(def visual-bounds (.getVisualBounds (fx/primary-screen)))

(def xyxy
  (let [vb ^Rectangle2D visual-bounds]
    [(.getMinX vb)
     (.getMinY vb)
     (+ (.getMinX vb) ^int launcher-width)
     (.getMaxY vb)]))

(def launcher-height (- ^int (xyxy 3) ^int (xyxy 1)))



;; TODO: sort out sizes for applet-tiles et al

(defn- launcher-applet-tile
  "Builds a 'tile' (a parent) containing a labeled button (for the launcher)."
  [applet-info tile-width main-wrapper]
  ;(println app-info)
  ;(pprint app-info)
  ;(println ":icon-fn:" (:icon-fn app-info) (type (:icon-fn app-info)))
  (let [
        {:keys [label description icon main dispose]} applet-info
        arc 6
        label-font-size 10
        button-width (- tile-width (* 2 arc))
        icon-width button-width
        dispose-fn
        (fn []
          (main-wrapper
            #(let [res (dispose)]
               (if (fx/node? res)
                   res
                   (styled/heading (format "'%s' unloaded" (label)))))))

        tile
        (fx/vbox

          (doto (Button. nil (icon icon-width icon-width))
            (fx/set-tooltip (description))
            (fx/set-onaction #(main-wrapper main))
            (.setPrefWidth button-width)
            (.setPrefHeight button-width)
            (.setStyle (format "-fx-background-radius: %s;" arc))
            (.setContextMenu
              (ContextMenu. (fxj/vargs
                              (doto (MenuItem. "Unload (dispose of) this applet")
                                    (fx/set-onaction dispose-fn))))))
          (doto (fx/label (label))
                (.setStyle (format "-fx-font-size: %spx;" label-font-size))
                (.setMaxWidth tile-width)
                (.setWrapText true)
                (.setTextAlignment TextAlignment/CENTER))

          :alignment fx/Pos_CENTER
          :spacing 5)]
    tile))


(defn- launcher-root
  "The Launcher root node.  Was previously the sole content of Launcher.
  Is now inserted as \"master\" in the master-detail setup of the application window."
  [detail-setter]  ;; a 1-arg fn. If arg is ^javafx.scene.Node, then that node gets set as "detail" in application window.
  (let [
        welcome-node
        (styled/heading "Welcome to George" :size 24)

        main-wrapper ;; a function which calls the applet-fn, and passes the return-value to details-setter
        #(detail-setter (when % (%)))

        george-icon
        (doto (ImageView. (Image. "graphics/George_icon_128_round.png"))
          (.setFitWidth tile-width)
          (.setFitHeight tile-width)
          (.setOnMouseClicked (fx/event-handler
                                (detail-setter
                                  (styled/heading "George" :size 24)))))

        about-label
        (doto
          (fx/label "About")
          (.setStyle "-fx-font-size: 10px;")
          (.setOnMouseClicked (fx/event-handler (about-stage))))

        applet-infos
        (applet/load-applets)

        applet-tiles-and-paddings
        (flatten
          (map #(vector
                  (padding 20)
                  (launcher-applet-tile % tile-width main-wrapper))
               applet-infos))

        root ^VBox
        (apply fx/vbox
               (concat
                 [
                  (padding 10)
                  george-icon
                  (padding 10)
                  (hr launcher-width)]

                 applet-tiles-and-paddings

                 [
                  (fx/region :vgrow :always)

                  (hr launcher-width)
                  (padding 5)
                  about-label
                  (padding 5)
                  :padding 5
                  :alignment fx/Pos_TOP_CENTER]))

        dispose-fn
        #(doseq [applet applet-infos]
           (try ((:dispose applet))
                (catch Exception e nil)))]

    (doto root
      (.setMaxWidth launcher-width)
      (.setMaxHeight launcher-height))

    (detail-setter welcome-node)

    [root dispose-fn]))



(defn- application-close-handler [^Stage application-stage dispose-fn]
  (fx/event-handler-2 [_ e]
     (.toFront application-stage)
     (let [repl? (boolean (env :repl?))
           button-index
           (fx/now
             (fx/alert
               (str "Do you want to quit George?"
                    (when repl? "\n\n(You are running from a repl.\n'Quit' will not exit the JVM instance.)"))
               :title "Quit?"
               :options [(str "Quit")]
               :owner application-stage
               :mode nil
               :cancel-option? true))
           exit? (= 0 button-index)]

          (if exit?
            (do (repl-server/stop!)
                (dispose-fn)
                (println "Bye for now!" (if repl? " ... NOT" ""))
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


(defn- morphe-launcher-stage [^Stage stage ^Pane application-root [x y w h :as target-bounds]]
  ;; Fade out old content.
  (fx/later
    (doto stage
          (.toFront)
          (.setTitle  "...")))

  (ui-stage/swap-with-fades stage (fx/borderpane) true 300)
  (let [
        x-prop (double-property (.getX stage) #(.setX stage %))
        y-prop (double-property (.getY stage) #(.setY stage %))
        w-prop (double-property (.getWidth stage) #(.setWidth stage %))
        h-prop (double-property (.getHeight stage) #(.setHeight stage %))
        [root-node dispose-fn] application-root]
    ;; Transition stage.
    (fx/synced-keyframe
      300
      [x-prop x]
      [y-prop y]
      [w-prop w]
      [h-prop h])
    ;; Fade in Launcher root
    (ui-stage/swap-with-fades stage root-node true 400)

    ;(.setOnKeyPressed (.getScene stage)
    ;                  (fx/key-pressed-handler
    ;                    {#{:ALT :Q}
    ;                     #(do
    ;                        (repl-server/stop!)
    ;                        (.hide stage))}))

    (fx/later
         ;; TODO: prevent fullscreen.  Where does the window go after fullscreen?!?
        (doto stage
          (.setTitle "George")
          (.setResizable true)
          (fx/setoncloserequest (application-close-handler stage dispose-fn))))))



(defn starting-stage
  "Called form Main/-start or launcher/main.
  Returns a small, centered stage, which will morph into the main application window."
  [& [^Stage stage]]
  (fx/now
    (if stage
        (doto stage
          (.setTitle "Loading ...")
          (.setScene (fx/scene (ui-stage/scene-root-with-child)
                               :size [240 80]))
          (.centerOnScreen)
          (.show)
          (.toFront))

        (starting-stage (fx/stage)))))



(defn application-root
  []
  (let [[master-detail-root master-setter detail-setter] (layout/master-detail)
        [l-root dispose-fn] (launcher-root detail-setter)]
    (master-setter l-root)
    [master-detail-root dispose-fn]))


(defn start
  "Three versions of this method allow for different startup-strategies. The result is always that a created or given stage will be transformed (animated) into the launcher stage."
  ([]
   (start (starting-stage)))
  ([stage]
   (start stage (application-root)))
  ([stage root]
   (morphe-launcher-stage stage root [0 0 1280 720])))



(defn -main
  "Launches George (launcher) as a standalone application."
  [& args]
  (println "george.application.launcher/-main"
           (if (empty? args)
             ""
             (str " args: " (apply str (interpose " " args)))))
  (fx/later (start)))


;;; DEV ;;;

;(when (env :repl?)  (-main))
;(when (env :repl?)  (start))
;(when (env :repl?)  (start (starting-stage)))

