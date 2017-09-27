;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.


(ns george.application.launcher

  (require
    [clojure.repl :refer [doc]]
    [clojure.java.browse :refer [browse-url]]
    [george.javafx :as fx]
    [george.application.applet-loader :as applets-loader]
    [george.util.singleton :as singleton]
    [george.application.repl-server :as repl-server]
    [clojure.java.io :as cio])

  (:import [javafx.scene.image ImageView Image]
           [javafx.scene.paint Color]
           [javafx.geometry Pos]
           [javafx.stage Screen]
           [javafx.application Platform]
           (javafx.scene.control Hyperlink)))



(defn- about-stage-create []
  (let [text
        (fx/label
          (format "
George version: %s
Clojure version: %s
Java version: %s


Copyright 2015-2017 Terje Dahl.
Powered by open source software.
"
             (slurp (cio/resource "george-version.txt"))
             (clojure-version)
             (System/getProperty "java.version")))
        link
        (doto (Hyperlink. "www.george.andante.no")
          (.setStyle "-fx-border-color: transparent;-fx-padding: 10 0 10 0;-fx-text-fill:#337ab7;")
          (.setOnAction (fx/event-handler (browse-url "http://www.george.andante.no"))))]
      (fx/stage
         :style :utility
         :sizetoscene true
         :title "About George"
         :onhidden #(singleton/remove ::about-stage)
         :scene (fx/scene
                  (fx/vbox
                    (ImageView. (Image. "graphics/George_logo.png"))
                    text
                    link
                    :padding 10
                    :background (fx/color-background Color/WHITE))))))

(def ABOUT_STAGE_KW ::about-stage)

(defn- about-stage []
  (if-let [st (singleton/get ABOUT_STAGE_KW)]
    (do (.hide st)
        (singleton/remove ABOUT_STAGE_KW))
    (singleton/get-or-create
      ABOUT_STAGE_KW about-stage-create)))


(defn- applet-button [{:keys [name description main-fn]} button-width]
  (fx/button name
             :width button-width
             :onaction main-fn
             :tooltip description))


(defn- launcher-scene []
    (let [
          b-width 150

          applet-info-list
          (applets-loader/load-applets)
          ;_ (println "  ## applet-info-seq:" applet-info-list)

          applet-buttons
          (map #(applet-button % b-width) applet-info-list)

          logo
          (ImageView. (Image. "graphics/George_logo.png"))

          ;; Set align BOTTOM_RIGHT
          about-button
          (doto
            (fx/label "About")
            (.setOnMouseClicked (fx/event-handler (about-stage))))

          about-box
          (fx/vbox (fx/region :vgrow :always)
            about-button
            :alignment Pos/BASELINE_LEFT)

          scene
          (fx/scene
             (apply fx/hbox
                    (flatten [logo applet-buttons about-box
                              :spacing 15
                              :padding 10
                              :alignment Pos/CENTER_LEFT
                              :background (fx/color-background Color/WHITE)])))]
             ;:size [180 (+ 80 ;; logo
             ;              (* 48  ;; each button
             ;                (+ (count applet-buttons)
             ;                   0)))])] ;; extra buttons

        scene))


(defn- launcher-close-handler [launcher-stage]
  (fx/event-handler-2 [_ e]
     (let [
           button-index
           (fx/now
             (fx/alert
               "Do you want to quit George?"
               :title "Quit?"
               :options ["Quit"]
               :owner launcher-stage
               :mode nil
               :cancel-option? true))
           exit? (= 0 button-index)]

          (if exit?
            (do (repl-server/stop!)
                (fx/now (Platform/exit))
                (System/exit 0))
            (.consume e))))) ;; do nothing


(defn show-launcher-stage [stage]
    (let [
          visual-bounds (.getVisualBounds (Screen/getPrimary))
          scene (launcher-scene)]

      (.setOnKeyPressed scene
                        (fx/key-pressed-handler
                          {#{:ALT :Q}
                           #(do
                              (repl-server/stop!)
                              (.hide stage))}))

      ;; TODO: prevent fullscreen.  Where does the window go after fullscreen?!?
      (doto stage
        (.setScene scene)
        (.setX (-> visual-bounds .getMinX (+ 0)))
        (.setY (-> visual-bounds .getMinY (+ 0)))
        (.setTitle "George")
        (.setResizable false)
        (fx/setoncloserequest (launcher-close-handler stage))
        (.show)
        (.toFront))))


;; called from Main
(defn start [stage]
  (show-launcher-stage stage))


(defn -main
  "Launches George (launcher) as a stand-alone application."
  [& args]
  (println "george.application.launcher/-main"
           (if (empty? args)
             ""
             (str " args: " (apply str (interpose " " args)))))
  (fx/now (show-launcher-stage (fx/stage :show false))))


;;; DEV ;;;

;(do (println "WARNING: Running george.application.launcher/-main") (-main))
