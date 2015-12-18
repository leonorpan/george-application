(ns dev.util.javafx.classes)


(defn import! []
"imports a pile of JavaFX classes into calling namespace"
    (import

        '[javafx.animation
          ScaleTransition SequentialTransition
          Timeline KeyFrame KeyValue]

        '[javafx.application
          Platform]

        '[javafx.collections
          FXCollections]

        '[javafx.embed.swing JFXPanel]

        '[javafx.event
          EventHandler]

        '[javafx.geometry
          Insets Orientation Pos VPos]

        '[javafx.scene
          Cursor Group Node Parent Scene
          SnapshotParameters]

        '[javafx.scene.canvas
          Canvas]

        '[javafx.scene.control
          Alert Alert$AlertType
          Button ButtonType ButtonBar$ButtonData
          Label
          ListView RadioButton
          TextField TextArea TextInputDialog]

        '[javafx.scene.effect
          Lighting]

        '[javafx.scene.image
          Image]

        '[javafx.scene.input
          Clipboard ClipboardContent Dragboard
          TransferMode
          MouseEvent]

        '[javafx.scene.layout
          BorderPane HBox Priority Region StackPane VBox
          Pane Border FlowPane
          BorderStroke BorderStrokeStyle CornerRadii BorderWidths]

        '[javafx.scene.paint
          Color]

        '[javafx.scene.text
          Font FontWeight Text TextAlignment]

        '[javafx.scene.shape
          Rectangle Shape StrokeLineCap StrokeType]

        '[javafx.stage
          Screen Stage StageStyle Window]

        '[javafx.util
          Duration]
        )
 nil )
