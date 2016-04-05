(ns george.javafx
    (:require
        [clojure.java.io :as cio]
        [clojure.string :as s]
        [george.java :as j] :reload
        [george.util :as u] :reload
        )
    (:import
              [javafx.animation
                ScaleTransition SequentialTransition TranslateTransition
                Timeline KeyFrame KeyValue]

              [javafx.application
                Application Platform]

              [javafx.beans.property
                StringProperty]

              [javafx.beans.value
                ChangeListener WritableValue]

              [javafx.collections
                FXCollections ListChangeListener]

              [javafx.embed.swing JFXPanel]

              [javafx.event
                EventHandler]

              [javafx.geometry
                Insets Orientation Pos VPos]

              [javafx.scene
                Cursor Group Node Parent Scene
                SnapshotParameters]

              [javafx.scene.canvas
                Canvas]

              [javafx.scene.control
                Alert Alert$AlertType
                Button ButtonType ButtonBar$ButtonData
                Label
                ListView RadioButton
                Menu MenuBar MenuItem
                OverrunStyle
                Tab TabPane
                TextField TextArea TextInputDialog
                Tooltip
                ScrollPane
                ]

              [javafx.scene.effect
                Lighting]

              [javafx.scene.image
                Image ImageView]

              [javafx.scene.input
                Clipboard ClipboardContent Dragboard
                TransferMode
                MouseEvent
                KeyEvent KeyCode]

              [javafx.scene.layout
                BorderPane HBox Priority Region StackPane VBox
                Pane Border FlowPane
                BorderStroke BorderStrokeStyle CornerRadii BorderWidths]

              [javafx.scene.paint
                Color]

              [javafx.scene.text
                Font FontWeight Text TextAlignment TextFlow]

              [javafx.scene.shape
                Circle Line Rectangle Shape StrokeLineCap StrokeType Polygon]

              [javafx.scene.web
                WebEngine WebView]

              [javafx.stage
                FileChooser FileChooser$ExtensionFilter Screen Stage StageStyle Window]

              [javafx.util
                Duration]
             )
)



(defn set-implicit-exit [b]
    (Platform/setImplicitExit false))

(defn ^:deprecated dont-exit! []
    (set-implicit-exit false))

(set-implicit-exit false)


(defn init []
    "An easy way to 'initalize [JavaFX] Toolkit'
Needs only be called once in the applications life-cycle.
Has to be called before the first call to/on FxApplicationThread (javafx/thread)"
    (JFXPanel.))

(init)




;; A nice combo for black text on white background
(def ANTHRECITE (Color/web "#2b292e")) ;; Almost black
(def WHITESMOKE Color/WHITESMOKE)  ;; off-white





(defn later*
    "Utility function for 'thread'."
    [expr]
    (if (Platform/isFxApplicationThread)
        (expr)
        (Platform/runLater expr)))

(defmacro ^:deprecated thread
    "Ensure running body in JavaFX thread: javafx.application.Platform/runLater"
    [& body]
    `(later* (fn [] ~@body)))


(defmacro later
    "Ensure running body in JavaFX thread: javafx.application.Platform/runLater"
    [& body]
    `(later* (fn [] ~@body)))


(defn now*
    "Ensure running body in JavaFX thread: javafx.application.Platform/runLater, but returns result. Prefer using 'later'"
    [expr]
    (if (Platform/isFxApplicationThread)
        (expr)
        (let [result (promise)]
            (later
                (deliver result (try (expr) (catch Throwable e e (println e))))
                ;(deliver result (expr))
                )
            @result)))


(defmacro now
    "Ensure running body in JavaFX thread: javafx.application.Platform/runLater, but returns result. Prefer using 'later'."
    [& body]
    `(now* (fn [] ~@body)))




(defmacro event-handler
    "Returns an instance of javafx.event.EventHander,
where input is ingored,
and the the body is called on 'handle' "

    [& body]
    `(reify EventHandler (~'handle [~'_ ~'_] ~@body)))

(defmacro event-handler-2
    "Returns an instance of javafx.event.EventHander,
where args-vec is a vector of 2 elements  - naming the bindings for 'this' and 'event',
and the body is called on 'handle'"
    [args-vec & body]
    (assert (vector? args-vec) "First argument must be a vector representing 2 args")
    (assert (= 2 (count args-vec)) "args-vector must contain 2 elements - for binding 'this' and 'event'")
    `(reify EventHandler (~'handle ~args-vec ~@body)))


; (event-handler (println 1) (println 2)) ->
; (reify EventHandler (handle [_ _] (println 1) (println 2)))
(comment macroexpand-1 '(event-handler
                    (println 1)
                    (println 2)))

; (event-handler-2 [t e] (println 1) (println 2)) ->
; (reify EventHandler (handle [t e] (println 1) (println 2)))
(comment macroexpand-1 '(event-handler-2 [t e]
                    (println 1)
                    (println 2)))



(defn XY [item]
    [(.getX item) (.getY item)])

(defn WH [item]
    (if (instance? Node item)
        (let [b (.getBoundsInParent item)]
            [(.getWidth b) (.getHeight b)])
        [(.getWidth item) (.getHeight item)]))


(defn make-border
    ([color]
        (make-border color 1.))
    ([color width]
        (make-border color width 0.))
    ([color width rad]
        (Border. (j/vargs
                     (BorderStroke.
                          color
                          BorderStrokeStyle/SOLID
                          (CornerRadii. rad)
                          (BorderWidths. width)
                          ))))
    )


(defn add-stylesheets [^Scene scene & sheetpaths]
    (-> scene .getStylesheets (.addAll (into-array sheetpaths))))

(defn add-stylesheet [^Scene scene ^String sheetpath]
    (-> scene .getStylesheets (.add sheetpath)))


(defn set-Modena []
    (Application/setUserAgentStylesheet Application/STYLESHEET_MODENA)

    )



;; http://code.makery.ch/blog/javafx-dialogs-official/
(defn show-actions-dialog [title header message options include-cancel-button]
    "returns index of selected option, else -1

    ex: (show-actions-dialog \"Title\" nil \"Message\" [\"A\" \"B\"] true)
    "
    (let [
             buttons
             (mapv
                 #(ButtonType. %)
                 options)
             buttons
             (if include-cancel-button
                 (conj buttons (ButtonType. "Cancel" ButtonBar$ButtonData/CANCEL_CLOSE))
                 buttons)
             result
             (.showAndWait
                 (doto (Alert. Alert$AlertType/CONFIRMATION)
                     (.setTitle title)
                     (.setHeaderText header)
                     (.setContentText message)
                     (-> .getButtonTypes (.setAll (into-array ButtonType buttons)))
                     ))
             ]
        (.indexOf options (-> result .get .getText))))





;; This is a hack!
;; loading fonts from CSS doesn't work now, if there is a space in the file path.
;; So we pre-load them here, and they should then be available in css
(comment let  [fonts [
    "SourceCodePro-Regular.ttf"
    "SourceCodePro-Medium.ttf"
    "SourceCodePro-Bold.ttf" ;; Bold seems to look just like Regular! (At least on my Mac)
    "SourceCodePro-Semibold.ttf" ]]
    (doseq [f fonts]
        (-> (format "fonts/%s" f) cio/resource str (s/replace "%20" " ") (Font/loadFont  12.))))


(defn SourceCodePro [weight size]  ;; "Regular" / "Medium", etc
    (-> (format "fonts/SourceCodePro-%s.ttf" weight)
        cio/resource
        str
        (s/replace "%20" " ")
        (javafx.scene.text.Font/loadFont (double size)))
    )







(defn keyframe*
    "creates an instance of Keyframe with duration (millis) and KeyValue-s from a seq of vectors of format [property value]"
    [duration keyvalues]
    (KeyFrame.
        (Duration. duration)
        (j/vargs*
            (map (fn [[p v]](KeyValue. p v))
                 (filter some? keyvalues)))))


(defn keyframe
    "creates an instance of Keyframe with duration (millis) and KeyValue-s from vectors of format [property value]"
    [duration & keyvalues]
    (keyframe* duration keyvalues))

javafx.animation.Timeline
(defn timeline
    "creates a timeline of instances of KeyFrame"
    [onfinished-fn & KeyFrames]
    (let [t (Timeline. (j/vargs* KeyFrames))]
        (if onfinished-fn
            (. t setOnFinished (event-handler (onfinished-fn))))
        t))


(defn simple-timeline
    "creates a timeline containing a single keyframe with duration (in millis),
    onfinished-fn (or nil),
    and keyvalues as vectors as per function 'keyframe'"
    [duration onfinished-fn & keyvalues]
    (timeline onfinished-fn (keyframe* duration keyvalues)))


(def NANO_PR_SEC
    "number of nano-seconds pr second"
    1000000000)

(def NANO_PR_MILLI
    "number of nano-seconds pr milli-second"
    1000000)

(def DEFAULT_TICKS_PR_SEC
    "default number of 'ticks' pr second"
    60)

;(set! *unchecked-math* :warn-on-boxed)
;(set! *warn-on-reflection* true)

(defn synced-keyframe
    "same as 'keyframe', but runs immediately in current thread"
    [duration & keyvalues]
    (when keyvalues
        (let [
              ;;  replace [prop end] with [prop start end]
              keyvalues (map
                 (fn [[prop end]] [prop (. ^WritableValue prop getValue) end])
                 (filter some? keyvalues))

              start-nano     ^long (System/nanoTime)
              duration-nano  (* duration NANO_PR_MILLI)
              end-nano       (+ start-nano duration-nano)
              sleep-nano     ^long (/ NANO_PR_SEC DEFAULT_TICKS_PR_SEC) ;; 60 fps
              ]
            (loop [current-nano start-nano
                   next-nano    (+ current-nano sleep-nano)]

                (when (<= current-nano end-nano)
                    (later (doseq [[^WritableValue prop start end] keyvalues]
                                ;;  cDv (* (/ cDt Dt) Dv)
                                ;; cDv  (* (/ (- current-time start-time) delta-time) (- e s))
                                (. prop  setValue
                                   (+ start (*
                                            (/ (- current-nano start-nano) duration-nano)
                                            (- end start))))))

                        (let [sleep-milli (int (/ (- next-nano current-nano) NANO_PR_MILLI))]
                            (if (> sleep-milli 0)
                                (Thread/sleep sleep-milli)))

                        (recur next-nano (+ current-nano sleep-nano))))
            ;; correct final value and "hold" until to ensure consistent state at end
            (now (doseq [[^WritableValue p _ e] keyvalues]
                       (. p setValue e))))))


(defn add [^Parent p ^Node n]
    (-> p .getChildren (. add n)))


(defn region
"optional kwargs:
    :hgrow :always/:never/:sometimes"

    [& {:keys [hgrow]
               :or   {
                      }}]

    (doto (Region.)
        (HBox/setHgrow
            ({
              :always Priority/ALWAYS
              :never Priority/NEVER
              :sometimes Priority/SOMETIMES
              } hgrow)
            )))


(defn group* [nodes]
    (Group. (j/vargs-t* Node nodes)))


(defn group
    ([& nodes]
     (group* nodes)))


(defn line [& {:keys [x1 y1 x2 y2 color width]
               :or   { x1 0 y1 0
                      x2 x1 y2 y1
                      color Color/BLACK
                      width 1
                      }}]
    (doto (Line. x1 y1 x2 y2)
        (. setStroke color)
        (. setStrokeWidth width)
        ))



(defn polygon
    [& args]

    (let [
          [points kwargs]
          (u/partition-args
              args
              {:fill Color/TRANSPARENT
               :stroke Color/BLACK
               :strokewidth 1.
               })
          ]
         (doto (Polygon. (j/vargs-t* Double/TYPE points))
             (. setFill (:fill kwargs))
             (. setStroke (:stroke kwargs))
             (. setStrokeWidth (:strokewidth kwargs))
             )))



(defn rectangle [& {:keys [x y width height fill arc]
                    :or   { x 0 y 0
                           width 50
                           height 50
                           fill Color/BLACK
                           arc 0
                           }}]

    (doto (Rectangle. x y width height)
        (. setFill fill)
        (. setArcWidth arc)
        (. setArcHeight arc)
        ))

(defn label
    ([] (Label.))
    ([text] (Label. text))
    )

(defn button [label & {:keys [onaction width minwidth tooltip]}]
    (let [b (Button. label)]
        (if width (. b setPrefWidth (double width)))
        (if minwidth (. b setMinWidth (double minwidth)))
        (if onaction (. b setOnAction (event-handler (onaction))))
        (if tooltip (. b setTooltip (Tooltip. tooltip)))
        b))


(defn textarea
    [& {:keys [text font]
         :or {text ""}}]
     (doto (TextArea. text)
         (. setFont font)))


(defn insets* [[top right bottom left]]
    (Insets. top right bottom left))


(defn insets
    ([v]
    (if (vector? v)
        (insets* v)
        (Insets. v)))
    ([top right bottom left]
     (insets* [top right bottom left])))


(defn box [vertical? & args]
    (let [
          [nodes kwargs]
          (u/partition-args
              args {:spacing 0
                    :insets 0
                    :padding 0
                    :alignment nil})
    ]
        (doto (if vertical?
                  (VBox. (:spacing kwargs) (j/vargs-t* Node nodes))
                  (HBox. (:spacing kwargs) (j/vargs-t* Node nodes)))
            (BorderPane/setMargin (insets (:insets kwargs)))
            (BorderPane/setAlignment (:alignment kwargs))
            (. setStyle (format "-fx-padding: %s %s;" (:padding kwargs) (:padding kwargs)))
            )
        ))

(defn hbox [& args]
    (apply box (cons false args)))

(defn vbox [& args]
    (apply box (cons true args)))

(defn borderpane
    "args:  & :center :top :right :bottom :left :insets"
    [ & {:keys [center top right bottom left] :as kwargs
         :or   {insets 0}}]
    (doto
        (BorderPane. center top right bottom left)
        (. setPadding (insets (:insets kwargs)))))



(defn scene [root & {:keys [size fill]
                     :or   {size [300 300]
                            fill  nil
                            }}]
    (Scene. root (double (first size)) (double (second size)) fill))



(defn ensure-handler [f]
    (if (instance? EventHandler f) f (event-handler (f))))


(defn centering-point-on-primary
    "returns [x y] for centering (stage) no primary screen"
    [scene-or-stage]
    (let [prim-bounds (. (Screen/getPrimary) getVisualBounds)]
        [ (-> prim-bounds .getWidth (/ 2) (- (/ (. scene-or-stage getWidth) 2)))
          (-> prim-bounds .getHeight (/ 2) (- (/ (. scene-or-stage getHeight) 2))) ]))

(defn stage [& {:keys [style title scene
                       sizetoscene location size
                       show ontop resizable
                       oncloserequest onhiding onhidden]
                :or   {style  StageStyle/DECORATED
                       title  "Untitled stage"
                       scene nil
                       sizetoscene false
                       location [50 50]
                       size [50 50]
                       show true
                       ontop false
                       resizable true
                       oncloserequest #()  ;; good for preventing closing (consume event)
                       onhiding #()  ;; god for saving content
                       onhidden #()  ;; god for removing references
                       }}]

    (let [stg (doto (Stage. style)
                  (. setTitle title)
                  (. setX (first location))
                  (. setY (second location))
                  (. setWidth (first size))
                  (. setHeight (second size))
                  (. setScene scene)
                  (. setAlwaysOnTop ontop)
                  (. setResizable resizable)
                  (. setOnCloseRequest (ensure-handler oncloserequest))
                  (. setOnHiding (ensure-handler onhiding))
                  (. setOnHidden (ensure-handler onhidden))
                  )
          ]


        (if show (. stg show))
        (if sizetoscene (. stg sizeToScene))
        stg))





(defn filechooserfilter [description & extensions]
    (FileChooser$ExtensionFilter. description (j/vargs* extensions)))


(def FILESCHOOSER_FILTERS_CLJ [
                               (filechooserfilter "Clojure Files" "*.clj")
                               (filechooserfilter "All Files"   "*.*")
                               ])


(defn filechooser [& filters]
    (doto (FileChooser.)
        (-> .getExtensionFilters
            (. addAll
                (j/vargs* filters)))))