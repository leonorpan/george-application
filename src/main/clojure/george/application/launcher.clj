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
    [clojure.java.io :as cio])

  (:import [javafx.scene.image ImageView Image]
           [javafx.scene.paint Color]
           [javafx.geometry Pos]
           [javafx.stage Screen]
           [javafx.application Platform]
           (javafx.scene.control Hyperlink)
           (javafx.beans.property SimpleDoubleProperty)))


(def about-tmpl "
George version: %s
Clojure version: %s
Java version: %s


Copyright 2015-2017 Terje Dahl.
Powered by open source software.
")


(defn- about-stage-create []
  (let [text
        (fx/label
          (format about-tmpl
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


(defn launcher-root-node []
  (let [
        tile-w 64
        w (+ tile-w 20)

        george-icon
        (doto (ImageView. (Image. "graphics/George_icon_128_round.png"))
          (.setFitWidth tile-w)
          (.setFitHeight tile-w))

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
                  (app-loader/launcher-app-tile % tile-w)
                  (app-loader/padding 30))
               app-infos))

        root
        (apply fx/vbox
               (concat
                 [
                  (app-loader/padding 10)
                  george-icon
                  (app-loader/padding 10)
                  (app-loader/hr w)
                  (app-loader/padding 30)]

                 app-tiles-and-paddings

                 [
                  (app-loader/hr w)
                  (app-loader/padding 5)
                  about-label
                  (app-loader/padding 5)
                  :padding 5
                  :alignment Pos/TOP_CENTER]))]

    (doto root
         (.setMaxWidth w)
         (.setMaxHeight (+ 10 tile-w 10 1 30
                          (* (+ tile-w 100) (count app-infos))
                          1 5 12 5)))))



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


(defn- double-property [init-value value-change-fn]
  (doto (SimpleDoubleProperty. init-value)
    (.addListener
      (fx/changelistener
        [_ _ _ new-val]
        (value-change-fn new-val)))))


(defn- morphe-launcher-stage [stage launcher-root]
  ;; Fade out old content.
  (fx/later (doto stage
              (.toFront)
              (.setTitle  "...")))

  (ui-stage/swap-with-fades stage (fx/borderpane) true 300)
  (let [
        visual-bounds (.getVisualBounds (Screen/getPrimary))
        target-x (-> visual-bounds .getMinX (+ 0))
        target-y (-> visual-bounds .getMinY (+ 0))
        target-w (.getMaxWidth launcher-root)
        target-h (.getMaxHeight launcher-root)

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
    (ui-stage/swap-with-fades stage launcher-root true 500)

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
(defn starting-stage []
  (fx/now
    (fx/stage :title "Loading ..."
              :scene (fx/scene (fx/stackpane (fx/text "Starting Launcher ..."))
                               :size [240 80])
              :tofront true)))


;; called from Main
(defn start
  "Three versions of this method allow for different startupstrategies. The result is always that a created or given stage will be transformed (animated) into the launcher stage."
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

;(do (println "WARNING: Running george.application.launcher/-main") (-main))
