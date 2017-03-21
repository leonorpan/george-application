(ns
  ^{:author "Terje Dahl"}
  george.core.overlayed
  (:require
    [george.javafx :as fx]

    [george.javafx.java :as fxj])


  (:import (javafx.scene.layout StackPane)
           (javafx.scene Node)
           (javafx.scene.control TextField)))


(defn- the-root []
  (let [some-text
        "Hello Word!
This is a printout.
It covers a textfield and a button.
Bla bla bla bla bla
asølkdfja sfd"

        a-button
        (doto
          (fx/button "Click" :onaction #(println "Click"))
          (.setFont(fx/SourceCodePro "BOLD" 20)))

        a-textfield
        (doto (TextField. "")
          (.setFont(fx/SourceCodePro "BOLD" 20)))

        a-textarea
        (doto
          (fx/textarea :text some-text
                       :font (fx/SourceCodePro "Regular" 14))
          (.setStyle "my-text-area-background: transparent;")
          (.setMouseTransparent true))

        pane
        (StackPane.
          (fxj/vargs-t Node
                       (fx/vbox
                         (fx/label "")
                         a-textfield
                         a-button)

                       a-textarea))]


    pane))


(defn- the-stage []
  (let [
        root (the-root)
        scene (doto
                (fx/scene root)
                (fx/add-stylesheet "styles/textarea.css"))

        stage
        (fx/now
          (fx/stage
            :title "Overlay test"
            :scene scene))]

    stage))


(defn -main [& args]
  (the-stage))


;;; DEV ;;;

;(println "WARNING: running george.core.overlayed/-main")  (-main)