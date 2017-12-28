;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns labs.window
  (:require
    [environ.core :refer [env]]
    [george.javafx :as fx]
    [george.javafx.java :as fxj])
  (:import
    (javafx.scene.layout TilePane)
    (javafx.geometry Orientation)))



(defn- load-app-action [root-pane app-pane left-pane app-root-fn]
  (fx/event-handler
    (doto app-pane (.setPrefColumns 1))
    (.unbind (.translateXProperty left-pane))
    (.play
      (fx/simple-timeline
        250
        #(.setCenter root-pane (app-root-fn))
        [(.translateXProperty left-pane) 0]))))


(defn- create-root []
  (let [button-width 70
        logo-b (doto (fx/button "Logo") (.setPrefWidth button-width))
        app1-b (doto (fx/button "App 1") (.setPrefWidth button-width))
        app2-b  (doto (fx/button "App 2") (.setPrefWidth button-width))
        app-buttons
        (doto
          (TilePane. (fxj/vargs app1-b app2-b))
          (.setOrientation Orientation/HORIZONTAL))
        left
        (doto (fx/vbox
                logo-b
                (fx/region :vgrow :always)
                app-buttons
                (fx/region :vgrow :always)
                (fx/label "About"))

          (.setAlignment fx/Pos_TOP_CENTER))
        root-pane
        (fx/borderpane :left left)

        set-app-action-fn
        (fn [app-b app-root-fn]
          (.setOnAction app-b (load-app-action root-pane app-buttons left app-root-fn)))]

    (set-app-action-fn app1-b  #(fx/label "App 1"))
    (set-app-action-fn app2-b  #(fx/label "App 2"))

    (.setOnAction logo-b
                  (fx/event-handler
                    (when-not (.isBound (.translateXProperty left))
                      (let [cnt (-> app-buttons .getChildren count)
                            offset (-> cnt (* button-width) (/ 2))]
                        (doto app-buttons (.setPrefColumns cnt))
                        (.setCenter root-pane nil)
                        (.play
                          (fx/simple-timeline
                            250
                            #(.bind (.translateXProperty left)
                                    (-> root-pane .widthProperty (.divide 2) (.subtract offset)))
                            [(.translateXProperty left)
                             (-> root-pane .getWidth (/ 2) (- offset))]))))))

    (.fire logo-b)

    root-pane))


(defn create-stage []
  (let []
    (fx/now
      (fx/stage
        :title "George"

        :scene (fx/scene
                 (create-root)
                 :size [600 400])))))



;;; DEV ;;;

;(when (env :repl?) (println "WARNING: Running george.application.window/create-stage") (create-stage))
