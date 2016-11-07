(ns no.andante.george.GeorgeApplication

  (:gen-class
    :main true
    :name no.andante.george.GeorgeApplication
    :extends javafx.application.Application)

  (:import (javafx.stage Stage StageStyle)
           (javafx.scene Scene Node)
           (javafx.scene.layout VBox)
           (javafx.geometry Pos Insets)
           (javafx.scene.control Label ToggleButton Button)
           ;(org.controlsfx.control SegmentedButton)
           (javafx.event EventHandler)
           (javafx.application Platform Application)))


(defn -start [this ^Stage stage]
  (println "start called")
  (println "params are:" (-> this .getParameters .getRaw seq))

  (doto stage
    (.setTitle "Hello World")
    (.initStyle StageStyle/UNDECORATED))

  (let [label
        (doto (Label. (.getTitle stage))
          (.setStyle "-fx-font-size: 25"))

        ;fxcontrol
        ;(SegmentedButton. (into-array [(ToggleButton. "One") (ToggleButton. "Two") (ToggleButton. "Three")]))

        exit-button
        (doto (Button. "Exit")
          (.setOnAction (reify EventHandler (handle [_ _] (Platform/exit)))))

        root (doto (VBox. (into-array Node [label
                                            ;fxcontrol
                                            exit-button]))
               (.setAlignment Pos/CENTER)
               (.setSpacing 20)
               (.setPadding (Insets. 25))
               (.setStyle "-fx-border-color: lightblue"))]

    (doto stage
      (.setScene (Scene. root))
      (.show))))


(defn -stop [this]
  (println "stop called"))


(defn -main
  "Launch the JavaFX Application using class no.andante.george.GeorgeApplication"
  [& args]
  (javafx.application.Application/launch no.andante.george.GeorgeApplication (into-array String args)))
