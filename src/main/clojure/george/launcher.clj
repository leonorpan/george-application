(ns george.launcher

    (require
        [clojure.repl :refer [doc]]

        [george.javafx.core :as fx]
        :reload

        [george.repl.input :as input]
        :reload
        [george.editor :as editor]
        :reload
        [george.output :as output]
        :reload)

    (:import [javafx.scene.image ImageView Image]
             [javafx.scene.paint Color]
             [javafx.geometry Pos]))



(defn- launcher-scene []
    (let [
          b-width 150

          output-button
          (fx/button
              "Output"
              :width b-width
              :onaction output/show-output-stage
              :tooltip "Open/show output-window")


          input-button
          (fx/button
              "Input"
              :width b-width
              :onaction #(do
                            ;(. output-button fire)
                            (input/new-input-stage))
              :tooltip "Open a new input window / REPL")


          code-button
          (fx/button
              "Code"
              :width b-width
              :onaction #(do
                            ;(. output-button fire)
                            (editor/new-code-stage))
              :tooltip "Open a new code editing window. \n(Can be used to open and save files.)")


          logo
          (ImageView. (Image. "graphics/George_logo.png"))

          scene
            (fx/scene
                (fx/vbox
                    logo input-button output-button code-button
                    :spacing 20
                    :padding 20
                    :alignment Pos/TOP_CENTER)
                :fill Color/WHITE ;; Doesn't take effect! :-(
                :size [180 220])]


        scene))


(defn- launcher-close-handler []
    (fx/event-handler-2 [_ e]
        (if
            (= -1
                (fx/show-actions-dialog
                    "Quit confirmation"
                    nil
                    "Do you want to quit George? (NOT IMPLEMENTED YET!) \n(TODO: handle open windows on exit!)"
                    ["Quit"]
                    true))
            (. e consume))))
            ;; else TODO: maybe do some exit-actions ...?



(defn show-launcher-stage []
    (let [
             scene
             (launcher-scene)

             stage
             (fx/now (fx/stage
                       :scene scene
                       :location [100 50]
                       :sizetoscene true
                       :title "George"
                       :ontop true
                       :resizable false
                       ;; TODO: prevent fullscreen.  Where does the window go after fullscreen?!?
                       :oncloserequest (launcher-close-handler)))]


        stage))




;;;; dev ;;;;


(defn -main
    "Launches George (launcher) as a stand-alone app."
    [& args]
    (println "george.launcher/-main"
             (if (empty? args)
                 ""
                 (str " args: " (apply str (interpose " " args)))))
    (fx/now (show-launcher-stage)))


;;; DEV ;;;

;(println "WARNING: Running george.laucher/-main" (-main))
