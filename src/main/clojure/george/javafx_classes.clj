(ns george.javafx-classes)


(defn ^:deprecated import-classes []
"imports a pile of JavaFX classes into calling namespace"
    (import

        '[javafx.animation
          ScaleTransition SequentialTransition
          Timeline KeyFrame KeyValue]

        '[javafx.application
          Application Platform]

        '[javafx.beans.value
          ChangeListener]

        '[javafx.collections
          FXCollections ListChangeListener]

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
          ScrollPane
          TextField TextArea TextInputDialog
          Tab TabPane
          Menu MenuBar MenuItem
          ]

        '[javafx.scene.effect
          Lighting]

        '[javafx.scene.image
          Image]

        '[javafx.scene.input
          Clipboard ClipboardContent Dragboard
          TransferMode
          MouseEvent
          KeyEvent KeyCode]

        '[javafx.scene.layout
          BorderPane HBox Priority Region StackPane VBox
          Pane Border FlowPane
          BorderStroke BorderStrokeStyle CornerRadii BorderWidths]

        '[javafx.scene.paint
          Color]

        '[javafx.scene.text
          Font FontWeight Text TextAlignment TextFlow]

        '[javafx.scene.shape
          Circle Rectangle Shape StrokeLineCap StrokeType]

        '[javafx.scene.web
          WebEngine WebView]

        '[javafx.stage
          FileChooser Screen Stage StageStyle Window]

        '[javafx.util
          Duration]
        )
 nil )
