;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.javafx
    (:require
        [clojure.java.io :as cio]
        [clojure.string :as s]
        [george.javafx.java :as fxj]
        [george.javafx.util :as fxu])

    (:import
        [javafx.animation
         Timeline KeyFrame KeyValue]

        [javafx.application
         Application Platform]

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
         SnapshotParameters SceneAntialiasing]

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
         ScrollPane CheckBox]

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
         BorderStroke BorderStrokeStyle CornerRadii BorderWidths Background BackgroundFill]

        [javafx.scene.paint
         Color]

        [javafx.scene.text
         Font FontWeight Text TextAlignment TextFlow]

        [javafx.scene.shape
         Circle Line Rectangle Shape StrokeLineCap StrokeType Polygon]

        [javafx.stage
         FileChooser FileChooser$ExtensionFilter Screen Stage StageStyle Window]

        [javafx.util
         Duration]
        (clojure.lang Keyword)))




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



(defn web-color [s]
    (Color/web s))


;; A nice combo for black text on white background
(def ANTHRECITE (Color/web "#2b292e")) ;; Almost black
(def WHITESMOKE Color/WHITESMOKE)  ;; off-white

(def RED Color/RED)
(def WHITE Color/WHITE)
(def BLUE Color/BLUE)
(def GREEN Color/GREEN)
(def BLACK Color/BLACK)

(def BLUE Color/BLUE)
(def GREY Color/GREY)



(def Pos_TOP_RIGHT Pos/TOP_RIGHT)
(def Pos_CENTER Pos/CENTER)
(def VPos_TOP VPos/TOP)
(def VPos_CENTER VPos/CENTER)
(def MouseEvent_ANY MouseEvent/ANY)





(defn color-background [color]
    (Background. (into-array [(BackgroundFill. color nil nil)])))


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
                (deliver result (try (expr) (catch Throwable e e (println e)))))
                ;(deliver result (expr))

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



(defmacro changelistener
    "Returns an instance of javafx.beans.value.ChangeListener,
where args-vec is a vector of 4 elements  - naming the bindings for 'this', 'observable', 'old', 'new',
and the body is called on 'changed'"
    [args-vec & body]
    (assert (vector? args-vec) "First argument must be a vector representing 4 args")
    (assert (= 4 (count args-vec)) "args-vector must contain 4 elements - for binding 'this', 'observable', 'old', 'new'")
    `(reify ChangeListener (~'changed ~args-vec
                               ~@body)))


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
     (Border. (fxj/vargs
                  (BorderStroke.
                       color
                       BorderStrokeStyle/SOLID
                       (CornerRadii. rad)
                       (BorderWidths. width))))))




(defn add-stylesheets [^Scene scene & sheetpaths]
    (-> scene .getStylesheets (.addAll (into-array sheetpaths))))

(defn add-stylesheet [^Scene scene ^String sheetpath]
    (-> scene .getStylesheets (.add sheetpath)))


(defn set-Modena []
    (Application/setUserAgentStylesheet Application/STYLESHEET_MODENA))



(defn option-index
  "returns the index of the selected option, or nil"
  [result options]
  (let [option-index (.indexOf options (-> result .get .getText))]
    (if (= option-index -1) nil option-index)))



(defn alert [message & args]
  "returns index of selected option, else nil

  ex: (actions-dialog \"Message\" :title \"Title\" :options [\"A\" \"B\"] :cancel-option? true)
  "
  (let [
        default-kwargs {
                        :title "Alert"
                        :header nil
                        :options ["OK"]
                        :mode :show-and-wait ;; :show-and-wait or :show
                        :owner nil
                        :cancel-option? false}

        [_ {:keys [options] :as kwargs}] (fxu/partition-args args default-kwargs)

        buttons
        (mapv #(ButtonType. %) options)
        buttons
        (if (:cancel-option? kwargs)
            (conj buttons (ButtonType. "Cancel" ButtonBar$ButtonData/CANCEL_CLOSE))
            buttons)

        alert
        (doto (Alert. Alert$AlertType/CONFIRMATION message (fxj/vargs* buttons))
          (.setTitle (:title kwargs))
          (.initOwner (:owner kwargs))
          (.setHeaderText (:header kwargs)))]

       (condp :mode kwargs
         :show-and-wait (option-index (.showAndWait alert) options)
         :show (option-index (.show alert) options)
         alert))) ;default - simply return the dialog itself






;; This is a hack!
;; loading fonts from CSS doesn't work now, if there is a space in the file path.
;; So we pre-load them here, and they should then be available in css
(comment let  [fonts [
                      "SourceCodePro-Regular.ttf"
                      "SourceCodePro-Medium.ttf"
                      "SourceCodePro-Bold.ttf" ;; Bold seems to look just like Regular! (At least on my Mac)
                      "SourceCodePro-Semibold.ttf"]]
    (doseq [f fonts]
        (-> (format "fonts/%s" f) cio/resource str (s/replace "%20" " ") (Font/loadFont  12.))))


(defn SourceCodePro [weight size]  ;; "Regular" / "Medium", etc
    (-> (format "fonts/SourceCodePro-%s.ttf" weight)
        cio/resource
        str
        (s/replace "%20" " ")
        (Font/loadFont (double size))))








(defn keyframe*
    "creates an instance of Keyframe with duration (millis) and KeyValue-s from a seq of vectors of format [property value]"
    [duration keyvalues]
    (KeyFrame.
        (Duration. duration)
        (fxj/vargs*
            (map (fn [[p v]](KeyValue. p v))
                 (filter some? keyvalues)))))


(defn keyframe
    "creates an instance of Keyframe with duration (millis) and KeyValue-s from vectors of format [property value]"
    [duration & keyvalues]
    (keyframe* duration keyvalues))


(defn timeline
    "creates a timeline of instances of KeyFrame"
    [onfinished-fn & KeyFrames]
    (let [t (Timeline. (fxj/vargs* KeyFrames))]
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
                         (fn [[prop end]] [prop (.getValue ^WritableValue prop) end])
                         (filter some? keyvalues))

              start-nano     ^long (System/nanoTime)
              duration-nano  (* duration NANO_PR_MILLI)
              end-nano       (+ start-nano duration-nano)
              sleep-nano     ^long (/ NANO_PR_SEC DEFAULT_TICKS_PR_SEC)] ;; 60 fps

          (when (> duration 0)
            (loop [current-nano start-nano
                   next-nano (+ current-nano sleep-nano)]
              (when (<= current-nano end-nano)
                (later
                  (doseq [[^WritableValue prop start end] keyvalues]
                    ;;  cDv (* (/ cDt Dt) Dv)
                    ;; cDv  (* (/ (- current-time start-time) delta-time) (- e s))
                    (.setValue prop
                       (+ start (*
                                  (/ (- current-nano start-nano) duration-nano)
                                  (- end start))))))

                (let [sleep-milli (int (/ (- next-nano current-nano) NANO_PR_MILLI))]
                  (if (> sleep-milli 0)
                    (Thread/sleep sleep-milli)))

                (recur next-nano (+ current-nano sleep-nano))))
            ;; correct final value and "hold" until to ensure consistent state at end
            (now (doseq [[^WritableValue p _ e] keyvalues]
                       (.setValue p  e)))))))


(defn observablearraylist-t [t & lst]
    (FXCollections/observableArrayList (into-array t lst)))


(defn observablearraylist [& lst]
    (FXCollections/observableArrayList (into-array lst)))


(defn listview
    ([]
     (listview
         (FXCollections/observableArrayList
             (fxj/vargs
                 "Julia", "Ian", "Sue", "Matthew", "Hannah", "Stephan", "Denise"))))

    ([observable-list]
     (ListView. observable-list)))



(defn multiline-listcell
  "Given a javafx.scene.control.ListView and a function which when passe an item, returns a string, this function returns a ListCell subclass based on javafx.scene.text.Text which can display multiple lines of text, and which wraps to fit within the width of th ListView."
  [listview item->str-fn]
  (proxy [javafx.scene.control.ListCell] []
    (updateItem [item is-empty]
      (proxy-super updateItem item is-empty)
      (.setText this nil)
      (.setPrefWidth this 0)
      (if (or is-empty (nil? item))
        (.setGraphic this nil)
        ;; else
        (.setGraphic this
                     (doto (javafx.scene.text.Text. (item->str-fn item))
                       (-> .wrappingWidthProperty (.bind (-> listview .widthProperty (.subtract 35))))))))))



(defn multiline-listcell-factory
  "Returns a new instance of multiline-listcell whenever called.
  See 'multiline-listview' and 'multiline-listcell'."
  [item->str-fn]
  (reify javafx.util.Callback
    (call [_ listview]
      (multiline-listcell listview item->str-fn))))



(defn multiline-listview
  "The provided function takes one argument, an item, and returns a string.
The function may be a keyword or simply 'str', or something more complex.
It must return a string (which may be wrapped to fit the width of the list."
  ([item->str-fn]
   (doto (javafx.scene.control.ListView.)
     (.setCellFactory (multiline-listcell-factory item->str-fn))))
  ([]
   (multiline-listview str)))




(defn add [^Parent p ^Node n]
    (-> p .getChildren (.add n)))


(defn set! [^Parent p ^Node n]
    (-> p .getChildren (.setAll (fxj/vargs n))))


(defn region
 "optional kwargs:
    :hgrow :always/:never/:sometimes"

    [& {:keys [hgrow]
              :or   {}}]


    (doto (Region.)
        (HBox/setHgrow
            ({
              :always Priority/ALWAYS
              :never Priority/NEVER
              :sometimes Priority/SOMETIMES}
             hgrow))))


(defn radiobutton []
    (RadioButton.))



(defn stackpane* [nodes]
    (StackPane. (fxj/vargs-t* Node nodes)))

(defn stackpane
    ([& nodes]
     (stackpane* nodes)))

(defn group* [nodes]
    (Group. (fxj/vargs-t* Node nodes)))


(defn group
    ([& nodes]
     (group* nodes)))


(defn line [& {:keys [x1 y1 x2 y2 color width smooth]
               :or   { x1 0 y1 0
                      x2 x1 y2 y1
                      color Color/BLACK
                      width 1
                      smooth true}}]

    (doto (Line. x1 y1 x2 y2)
        (.setStroke color)
        (.setStrokeWidth width)
        (.setSmooth smooth)))





(defn polygon
    [& args]

    (let [
          [points kwargs]
          (fxu/partition-args
              args
              {:fill Color/TRANSPARENT
               :stroke Color/BLACK
               :strokewidth 1.})]


         (doto (Polygon. (fxj/vargs-t* Double/TYPE points))
             (. setFill (:fill kwargs))
             (. setStroke (:stroke kwargs))
             (. setStrokeWidth (:strokewidth kwargs)))))


(defn rectangle [& args]
    (let [default-kwargs
          {:location [0 0]
           :size [50 50]
           :fill Color/BLACK
           :arc 0}

          [_ kwargs] (fxu/partition-args args default-kwargs)

          location (:location kwargs)
          size (:size kwargs)
          arc (:arc kwargs)]



      (doto (Rectangle.
                (first location)
                (second location)
                (first size)
                (second size))
          (. setFill (:fill kwargs))
          (. setArcWidth arc)
          (. setArcHeight arc))))


(defn label
    ([] (Label.))
    ([text] (Label. text)))


(defn set-tooltip [control s]
  (.setTooltip control (Tooltip. s))
  control)


(defn button [label & {:keys [onaction width minwidth tooltip]}]
    (let [b (Button. label)]
        (if width (. b setPrefWidth (double width)))
        (if minwidth (. b setMinWidth (double minwidth)))
        (if onaction (. b setOnAction (event-handler (onaction))))
        (if tooltip (set-tooltip b tooltip))
        b))


(defn checkbox [label & {:keys [onaction tooltip]}]
  (let [cb (CheckBox. label)]
    (when onaction (.setOnAction cb (event-handler (onaction))))
    (when tooltip (.setTooltip cb (Tooltip. tooltip)))
    cb))




(defn textfield
  [& {:keys [text font prompt]
      :or {text ""
           prompt ""
           font nil}}]
  (let [tf
        (doto (TextField. text)
          (.setPromptText prompt))]
    ;(when font (.setFont ta font))
    tf))


(defn textarea
    [& {:keys [text font prompt]
         :or {text ""
              prompt ""
              font nil}}]
    (let [ta
          (doto (TextArea. text)
            (.setPromptText prompt))]
        ;(when font (.setFont ta font))
      ta))


(defn text
    [s & {:keys [font]
          :or {}}]
    (doto (Text. s)
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


(defn set-padding
    ([pane v]
     (.setPadding pane (insets v)))
    ([pane t r b l]
     (.setPadding pane (insets t r b l))))



(defn box [vertical? & args]
    (let [
          [nodes kwargs]
          (fxu/partition-args
              args {:spacing 0
                    :insets 0
                    :padding 0
                    :alignment nil})]
        (doto (if vertical?
                  (VBox. (:spacing kwargs) (fxj/vargs-t* Node nodes))
                  (HBox. (:spacing kwargs) (fxj/vargs-t* Node nodes)))
            (BorderPane/setMargin (insets (:insets kwargs)))
            (.setAlignment  (:alignment kwargs))
            (.setStyle (format "-fx-padding: %s %s;" (:padding kwargs) (:padding kwargs))))))



(defn hbox [& args]
    (apply box (cons false args)))

(defn vbox [& args]
    (apply box (cons true args)))

(defn borderpane
    "args:  & :center :top :right :bottom :left :insets"
    [ & args]
    (let [
          default-kwargs
          {:center nil :top nil :right nil :bottom nil :left nil
           :insets 0}
          [_ kwargs] (fxu/partition-args args default-kwargs)]

      (doto
          (BorderPane. (:center kwargs)
                       (:top kwargs) (:right kwargs) (:bottom kwargs) (:left kwargs))
          (.setPadding (insets (:insets kwargs))))))



(defn scene [root & args]
            (let [
                  default-kwargs
                  {:size         nil ;[300 300]
                   :depthbuffer  false
                   :fill         nil
                   ;:antialiasing SceneAntialiasing/BALANCED  ;; set to nil if not required
                   :antialiasing nil}  ;; set to nil due to "upside-down" bug on Mac/Linux

                  [_ kwargs] (fxu/partition-args args default-kwargs)
                  size (:size kwargs)]

                (doto (if size
                          (Scene. root
                                  (double (first size))
                                  (double (second size))
                                  (:depthbuffer kwargs)
                                  (:antialiasing kwargs))
                          (Scene. root))
                    (.setFill (:fill kwargs)))))





(defn ensure-handler [f]
    (if (instance? EventHandler f) f (event-handler (f))))


(defn centering-point-on-primary
    "returns [x y] for centering (stage) no primary screen"
    [scene-or-stage]
    (let [prim-bounds (. (Screen/getPrimary) getVisualBounds)]
        [ (-> prim-bounds .getWidth (/ 2) (- (/ (. scene-or-stage getWidth) 2)))
          (-> prim-bounds .getHeight (/ 2) (- (/ (. scene-or-stage getHeight) 2)))]))

(defn imageview [rsc-str]
    (ImageView. (Image. rsc-str)))


(defn screens []
    (Screen/getScreens))

(defn primary-screen []
    (Screen/getPrimary))


(defn stagestyle [style-kw]
    (get {:decorated StageStyle/DECORATED
          :transparent StageStyle/TRANSPARENT
          :undecorated StageStyle/UNDECORATED
          :unified StageStyle/UNIFIED
          :utility StageStyle/UTILITY}
         style-kw
         StageStyle/DECORATED))


(defn setoncloserequest [stage fn-or-handler]
    (.setOnCloseRequest stage (ensure-handler fn-or-handler))
    stage)

(defn setonhiding [stage fn-or-handler]
    (.setOnHiding stage (ensure-handler fn-or-handler))
    stage)

(defn setonhidden [stage fn-or-handler]
    (.setOnHidden stage (ensure-handler fn-or-handler))
    stage)

(defn scrollpane [& [node]]
  (if node
    (ScrollPane. node)
    (ScrollPane.)))

(defn stage [& args]
    (let [
          default-kwargs
          {:style  :decorated
           :title  "Untitled stage"
           :scene nil
           :sizetoscene true
           :location nil ;[100 100]
           :size nil ;[200 200]
           :show true
           :ontop false
           :resizable true
           :oncloserequest #()  ;; good for preventing closing (consume event)
           :onhiding #()  ;; good for saving content
           :onhidden #()}  ;; good for removing references to self

          [_ kwargs] (fxu/partition-args args default-kwargs)]

      (let [stg (doto (Stage. (stagestyle (:style kwargs)))
                    (.setTitle (:title kwargs))
                    (.setScene (:scene kwargs))
                    (.setAlwaysOnTop (:ontop kwargs))
                    (.setResizable (:resizable kwargs))

                    (setoncloserequest (:oncloserequest kwargs))
                    (setonhiding (:onhiding kwargs))
                    (setonhidden (:onhidden kwargs)))]

          (when (:sizetoscene kwargs) (.sizeToScene stg))

          (when-let [[w h] (:size kwargs)]
              (doto stg (.setWidth w) (.setHeight h)))

          (when-let [[x y] (:location kwargs)]
              (doto stg (.setX x) (.setY y)))

          (when (:show kwargs) (.show stg))

          stg)))




(defn filechooserfilter [description & extensions]
    (FileChooser$ExtensionFilter. description (fxj/vargs* extensions)))


(def FILESCHOOSER_FILTERS_CLJ [
                               (filechooserfilter "Clojure Files" "*.clj")
                               (filechooserfilter "All Files"   "*.*")])



(defn filechooser [& filters]
    (doto (FileChooser.)
        (-> .getExtensionFilters
            (. addAll
               (fxj/vargs* filters)))))



(def sample-codes-map {
                       #{:S} #(println "S")
                       #{:S :SHIFT} #(println "SHIFT-S")
                       #{:S :CTRL} #(println "CTRL-S")
                       #{:S :ALT} #(println "ALT-S")
                       #{:S :SHIFT :CTRL} (event-handler (println "SHIFT-CTRL/CMD-S"))
                       #{:CTRL :ENTER} (event-handler-2 [_ event] (println "CTRL/CMD-ENTER") (. event consume))})



(def sample-chars-map {
                       "a" #(println "a")
                       "A" #(println "A")
                       " " (event-handler-2 [_ e] (println "SPACE (consumed)") (. e consume))})



;; TODO make macro that does this:
;; (condmemcall true (toUpperCase))
;;  => (fn [inst] (if true (. inst  (toUpperCase)) inst)
;; test:
;; (doto "A" .toLowerCase (fn [inst] (if (= 1 2) (. inst  (toUpperCase)) inst)))


(defn key-pressed-handler
 "Takes a map where the key is a set of keywords and the value is a no-arg function to be run or an instance of EventHandler.

The keywords int the set must be uppercase and correspond to the constants of javafx.scene.input.KeyCode.
Use :SHIFT :CTRL :ALT for plattform-independent handling of these modifiers (CTRL maps to Command on Mac).
If the value is a function, then it will be run, and then the event will be consumed.
If the value is an EventHandler, then it will be called with the same args as this handler, and it must itself consume the event if required.

Example of codes-map:
{   #{:S}              #(println \"S\")  ;; event consumed
    #{:S :SHIFT}       #(println \"SHIFT-S\")
    #{:S :SHIFT :CTRL} (fx/event-handler (println \"SHIFT-CTRL/CMD-S\"))  ;; event not consumed
    #{:CTRL :ENTER}    (fx/event-handler-2 [_ event] (println \"CTRL/CMD-ENTER\") (. event consume))
    }"
    [codes-map]
    (event-handler-2
        [inst event]
        ;(println "  ## inst:" inst "  source:" (. event getSource))
        (let [
              code (str (. event getCode))
              shift (when (. event isShiftDown) "SHIFT")
              shortcut (when (. event isShortcutDown) "CTRL")  ;; SHORTCUT CTRL/CMD  "C-"
              alt (when (. event isAltDown) "ALT") ;;  "M-"
              combo (set (map keyword (filter some? [code shift shortcut alt])))]
              ;_ (println "combo:" (str combo))

            (when-let [v (codes-map combo)]
                (if (instance? EventHandler v)
                    (. v handle event)
                    (do ;; else
                        (v)
                        (. event consume)))))))



(defn char-typed-handler
    "Similar to `key-pressed-handler`,
    but takes a map where the key case-sensitive 1-character string and the value is a no-arg function to be run or an instance of EventHandler.

    If the value is a function, then it will be run, but then the event will *not* be consumed. (You are probably typing text.)
    If the value is an EventHandler, then it will be called with the same event as this handler, and it must itself consume the event if required.

    Example of chars-map:
    {   \"s\"    #(println \"s\")  ;; event not consumed
        \"S\"    #(println \"S\")
        \"{\"    (fx/event-handler (println \"{\"))  ;; event not consumed
        \" \"    (fx/event-handler-2 [_ event] (println \"SPACE (consumed)\") (. event consume))
        }"
    [chars-map]

    (event-handler-2
        [inst event]
        (let [ch-str (. event getCharacter)]
            (when-let [v (chars-map ch-str)]
                (if (instance? EventHandler v)
                    (. v handle event)
                    (v))))))

