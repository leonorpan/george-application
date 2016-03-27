(ns george.javafx
    (:require
        [clojure.java.io :as cio]
        [clojure.string :as s]

        [george.java :as j] :reload
        [george.util :as u] :reload
;        [george.javafx-classes :as fxc] :reload
        )
    (:import [sun.font FontScaler]
             [com.sun.org.apache.xerces.internal.impl.dv DVFactoryException])

)


(def classes [
        '[javafx.animation
          ScaleTransition SequentialTransition TranslateTransition
          Timeline KeyFrame KeyValue]

        '[javafx.application
          Application Platform]

        '[javafx.beans.value
          ChangeListener WritableValue]

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
          Circle Line Rectangle Shape StrokeLineCap StrokeType Polygon]

        '[javafx.scene.web
          WebEngine WebView]

        '[javafx.stage
          FileChooser Screen Stage StageStyle Window]

        '[javafx.util
          Duration]
        ])

(defn import-classes! []
    (j/import! classes))

(import-classes!)


;; A cool colors
(def ANTHRECITE (Color/web "#2b292e"))
(def WHITESMOKE Color/WHITESMOKE)


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
    (-> scene .getStylesheets (.addAll (into-array  sheetpaths))))

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
    (-> (format "fonts/SourceCodePro-%s.ttf" weight) cio/resource str (s/replace "%20" " ") (Font/loadFont  size))
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
     (if (empty? args)
         (Polygon.)
         (let [
               defaults {
                         :fill Color/TRANSPARENT
                         :stroke Color/BLACK
                         :strokewidth 1.
                         }
               [points kwargs] (u/args-kwargs args)
               kwargs (merge defaults kwargs)

               ]
         (doto (Polygon. (j/vargs-t* Double/TYPE points))
             (. setFill (:fill kwargs))
             (. setStroke (:stroke kwargs))
             (. setStrokeWidth (:strokewidth kwargs))
             ))))


(defn rectangle [& {:keys [x y width height fill arc]
                    :or   { x 0 y 0
                           width 50
                           height 50
                           fill nil
                           arc 0
                           }}]

    (doto (Rectangle. x y width height)
        (. setFill fill)
        (. setArcWidth arc)
        (. setArcHeight arc)
        ))


(defn scene [root & {:keys [width height fill]
                     :or   {width 600.
                            height 400.
                            fill  nil
                            }}]
    (Scene. root width height fill))


(defn stage [& {:keys [style title scene x y width height show onclose]
                :or   {style  StageStyle/DECORATED
                       title  "Untitled stage"
                       scene nil
                       width  600
                       height 600
                       show true
                       onclose #()
                       }}]

    (let [stg (doto (Stage. style)
                  (. setTitle title)
                  (. setWidth width)
                  (. setHeight height)
                  (. setScene scene)
                  (. setOnCloseRequest (event-handler (onclose)))
                  )
          ]

        (if show (. stg show))
        stg))


