(ns george.app.launcher

  (require
    [clojure.repl :refer [doc]]
    [george.javafx :as fx]
    [george.app.applet-loader :as applets-loader])

  (:import [javafx.scene.image ImageView Image]
           [javafx.scene.paint Color]
           [javafx.geometry Pos]
           (javafx.stage Screen)
           (javafx.application Platform)))

(defn- about []

  (fx/stage
            :style :utility
            :size [250 200]
            :scene (fx/scene
                     (fx/vbox
                       (ImageView. (Image. "graphics/George_logo.png"))
                       (fx/label
                         "George: \n  Version: 0.7.3 \n\nClojure:\n  Version: 1.8.0\n")))

            :sizetoscene false
            :title "About George"))

(defn- applet-button [{:keys [name description main-fn] :as applet-info} button-width]
  (fx/button name
             :width button-width
             :onaction main-fn
             :tooltip description))


(defn- launcher-scene []
    (let [
          b-width 150

          applet-info-list
          (applets-loader/load-applets)
          _ (println "  ## applet-info-seq:" applet-info-list)

          applet-buttons
          (map #(applet-button % b-width) applet-info-list)

          ;output-button
          ;(fx/button
          ;    "Output"
          ;    :width b-width
          ;    :onaction output/show-output-stage
          ;    :tooltip "Open/show output-window")


          ;input-button
          ;(fx/button
          ;    "Input"
          ;    :width b-width
          ;    :onaction #(do
          ;                  ;(. output-button fire)
          ;                  (input/new-input-stage))
          ;    :tooltip "Open a new input window / REPL")


          ;code-button
          ;(fx/button
          ;    "Code"
          ;    :width b-width
          ;    :onaction #(do
          ;                  ;(. output-button fire)
          ;                  (editor/new-code-stage))
          ;    :tooltip "Open a new code editing window. \n(Can be used to open and save files.)")


          logo
          (ImageView. (Image. "graphics/George_logo.png"))


          ;; Set align BOTTOM_RIGHT
          about-button
          (doto
            (fx/label "About")
            (.setOnMouseClicked (fx/event-handler (about))))

          about-box
          (fx/vbox

            (doto (javafx.scene.layout.Region.)
              (javafx.scene.layout.VBox/setVgrow
                (javafx.scene.layout.Priority/ALWAYS)))

            about-button
            :alignment Pos/BASELINE_LEFT)

          scene
          (fx/scene
             (doto
               (apply fx/hbox
                      (flatten [logo applet-buttons about-box
                                :spacing 15
                                :padding 10
                                :alignment Pos/CENTER_LEFT]))
               (.setBackground (fx/color-background Color/WHITE)))

             :fill Color/WHITE)] ;; Doesn't take effect! Root panes background covers it! :-(
             ;:size [180 (+ 80 ;; logo
             ;              (* 48  ;; each button
             ;                (+ (count applet-buttons)
             ;                   0)))])] ;; extra buttons

        scene))


(defn- launcher-close-handler [launcher-stage]
  (fx/event-handler-2 [_ e]
     (let [
           button-index
           (fx/alert
             "Do you want to quit George?"
             :title "Quit?"
             :options ["Quit"]
             :owner launcher-stage
             :mode nil
             :cancel-option? true)]

          (if (= 0 button-index)
              (fx/later (Platform/exit))
              (.consume e))))) ;; do nothing


(defn show-launcher-stage [stage]
    (let [
          visual-bounds (.getVisualBounds (Screen/getPrimary))
          scene (launcher-scene)]

      (.setOnKeyPressed scene (fx/key-pressed-handler {#{:ALT :Q } #(.hide stage)}))

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
  "Launches George (launcher) as a stand-alone app."
  [& args]
  (println "george.app.launcher/-main"
           (if (empty? args)
             ""
             (str " args: " (apply str (interpose " " args)))))
  (fx/now (show-launcher-stage (fx/stage :show false))))


;;; DEV ;;;

;(println "WARNING: Running george.app.launcher/-main" (-main))
