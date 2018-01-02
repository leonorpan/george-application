;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.javafx
  (:require
    [clojure.java.io :as cio]
    [clojure
     [string :as cs]
     [pprint :refer [pprint]]]
    [george.javafx
     [java :as fxj]
     [util :as fxu]]
    [george.util.javafx :as ufx])
  (:import
    [javafx.animation Timeline KeyFrame KeyValue]
    [javafx.application Application Platform]
    [javafx.beans.value ChangeListener WritableValue]
    [javafx.collections FXCollections]
    [javafx.embed.swing JFXPanel]
    [javafx.event EventHandler]
    [javafx.geometry Insets Pos VPos Side]
    [javafx.scene Group Node Parent Scene]
    [javafx.scene.control
     Alert Alert$AlertType
     Button ButtonType ButtonBar$ButtonData
     Label
     ListView RadioButton
     TextField TextArea
     Tooltip
     ScrollPane CheckBox]
    [javafx.scene.image Image ImageView]
    [javafx.scene.input MouseEvent]
    [javafx.scene.layout
     BorderPane HBox Priority Region StackPane VBox
     Border
     BorderStroke BorderStrokeStyle CornerRadii BorderWidths Background BackgroundFill]
    [javafx.scene.paint Color Paint]
    [javafx.scene.text Font Text FontPosture FontWeight]
    [javafx.scene.shape Line Rectangle Polygon]
    [javafx.stage FileChooser FileChooser$ExtensionFilter Screen Stage StageStyle]
    [javafx.util Duration]))


;(set! *warn-on-reflection* true)


(defn init-toolkit
    "An easy way to 'initalize [JavaFX] Toolkit'
Needs only be called once in the applications life-cycle.
Has to be called before the first call to/on FxApplicationThread (javafx/later)"
  []
  (println (str *ns*"/init-toolkit ..."))
  (JFXPanel.))

;; Must be called here, else the rest of the file won't load!
(init-toolkit)


(defn set-implicit-exit [b]
  (println (str *ns*"/set-implicit-exit " b))
  (Platform/setImplicitExit false))

(set-implicit-exit false)


;;;;;;;;;


(defn web-color [s & [opacity]]
  (Color/web s (if opacity opacity 1.0)))


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



(def Pos_TOP_LEFT Pos/TOP_LEFT)
(def Pos_TOP_RIGHT Pos/TOP_RIGHT)
(def Pos_TOP_CENTER Pos/TOP_CENTER)
(def Pos_CENTER Pos/CENTER)
(def VPos_TOP VPos/TOP)
(def VPos_CENTER VPos/CENTER)

(def MouseEvent_ANY MouseEvent/ANY)


(defn corner-radii [rad]
  (when rad
    (if (vector? rad)
      (let [[tl tr br bl ] rad] (CornerRadii. tl tr br bl false))
      (CornerRadii. rad))))


(defn color-background [^Paint color & [rad insets]]
    (Background. (fxj/vargs (BackgroundFill. color (corner-radii rad) insets))))


(defn set-background [^Region r paint-or-background]
  (if (instance? Background paint-or-background)
    (.setBackground r  paint-or-background)
    (if (instance? Paint paint-or-background)
      (.setBackground r (color-background paint-or-background))
      (throw (IllegalArgumentException.
               (format "Don't know how to convert %s to javafx.scene.layout.Background" paint-or-background))))))


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


(defmacro thread-later
  "Runs the body in a fn in a later* on a separate thread"
  [& body]
  `(.start (Thread. (later* (fn [] ~@body)))))


(defn now*
    "Ensure running body in JavaFX thread: javafx.application.Platform/runLater, but returns result. Prefer using 'later'"
    [expr]
    (if (Platform/isFxApplicationThread)
        (expr)
        (let [result (promise)]
            (later
                (deliver result (try (expr) (catch Throwable e e (println e)))))

            @result)))


(defmacro now
    "Ensure running body in JavaFX thread: javafx.application.Platform/runLater, but returns result. Prefer using 'later'."
    [& body]
    `(now* (fn [] ~@body)))


(defmacro event-handler
    "Returns an instance of javafx.event.EventHander,
where input is ignored,
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


(defn ensure-handler [f]
  (if (instance? EventHandler f) f (event-handler (f))))


(defmacro ^ChangeListener changelistener
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


(defn children [^Parent parent]
  (.getChildren parent))

(defn children-set-all [^Parent parent children]
  (.setAll (.getChildren parent) children))


(defn XY [item]
    [(.getX item) (.getY item)])

(defn WH [item]
    (if (instance? Node item)
        (let [b (.getBoundsInParent item)]
            [(.getWidth b) (.getHeight b)])
        [(.getWidth item) (.getHeight item)]))


(defn set-translate-XY [^Node n [x y]]
  (doto n
    (.setTranslateX x)
    (.setTranslateY y)))


(defn new-border
    ([color]
     (new-border color 1.))
    ([color width]
     (new-border color width 0.))
    ([color width rad]
     (Border. (fxj/vargs
                  (BorderStroke. color
                                 BorderStrokeStyle/SOLID
                                 (corner-radii rad)
                                 (if (vector? width)
                                     (let [[t r b l] width] (BorderWidths. t r b l))
                                     (BorderWidths. width)))))))


(import
  '[com.sun.javafx.css.parser CSSParser]
  '[com.sun.javafx.css Stylesheet FontFace FontFace$FontFaceSrc]
  '[com.sun.javafx.util Logging]
  '[sun.util.logging PlatformLogger$Level])


(defn- load-fontface-src [^FontFace$FontFaceSrc ffsrc]
  (-> ffsrc .getSrc (cs/replace "%20" " ") (Font/loadFont 10.)))

(defn- load-fontface [^FontFace ff]
  (map load-fontface-src (.getSources ff)))

(defn- load-fonts [^Stylesheet stylesheet]
  (vec (flatten (map load-fontface (.getFontFaces stylesheet))))) ;; 'vec' ensures the lazy seq is realized

(defn stylesheet-parsed [path]
  (.parse (CSSParser.) (cio/resource path)))


(defn load-fonts-from-stylesheet
  "This does not add the stylesheet to the scene.
In stead it only parses the stylesheet and loads any font-faces found in it.
That way one avoids warnings and errors, yet the fonts are available.

Ensure that the passed-in stylesheet only contains font-info. Nothing else."
  [path]
  (println (format "%s/load-fonts-from-stylesheet '%s' ..." *ns* path))
  (-> path stylesheet-parsed load-fonts)) ;; parse and load fonts


;; Fonts need to be loaded early, for where fonts are called for in code, rather than in CSS.
(load-fonts-from-stylesheet "fonts/fonts.css")


(defn add-stylesheet [^Scene scene path]
  (let []
        ;logger (Logging/getCSSLogger)
        ;level (.level logger)]
    ;(.setLevel logger PlatformLogger$Level/OFF)  ;; turn off logger. Doesn't work well.
    (-> scene .getStylesheets (.add path))))  ;; set stylesheet
    ;(.setLevel logger level))) ;; turn logger back to previous level
    ;(load-fonts-from-stylesheet path)))


(defn add-stylesheets [scene & paths]
  (mapv #(add-stylesheet scene %) paths))


(defn set-Modena []
    (Application/setUserAgentStylesheet Application/STYLESHEET_MODENA))


(defn option-index
  "returns the index of the selected option, or nil"
  [result options]
  (let [option-index (.indexOf options (-> result .get .getText))]
    (if (= option-index -1) nil option-index)))


(def alert-types {
                  :none Alert$AlertType/NONE
                  :information Alert$AlertType/INFORMATION
                  :warning Alert$AlertType/WARNING
                  :confirmation Alert$AlertType/CONFIRMATION
                  :error Alert$AlertType/ERROR})


(defn alert [message & args]
  "returns index of selected option, else nil

  ex: (actions-dialog \"Message\" :title \"Title\" :options [\"A\" \"B\"] :cancel-option? true)

  In this example \"A\" will return 0, \"B\" will return 1, cancel will return nil.
  "
  (let [default-kwargs {:title "Info"
                        :header nil
                        :options ["OK"]
                        :mode :show-and-wait ;; :show-and-wait or :show
                        :owner nil
                        :cancel-option? false
                        :type :information}

        [_ {:keys [options] :as kwargs}] (fxu/partition-args args default-kwargs)

        buttons
        (mapv #(ButtonType. %) options)
        buttons
        (if (:cancel-option? kwargs)
            (conj buttons (ButtonType. "Cancel" ButtonBar$ButtonData/CANCEL_CLOSE))
            buttons)

        alert
        (doto (Alert. (alert-types type) message (fxj/vargs* buttons))
          (.setTitle (:title kwargs))
          (.initOwner (:owner kwargs))
          (.setHeaderText (:header kwargs)))]

       (condp :mode kwargs
         :show-and-wait (option-index (.showAndWait alert) options)
         :show (option-index (.show alert) options)
         alert))) ;default - simply return the dialog itself


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
            (.setOnFinished t  (event-handler (onfinished-fn))))
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


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)

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
              duration-nano  (* ^int duration ^int NANO_PR_MILLI)
              end-nano       (+ start-nano duration-nano)
              ^int sleep-nano     (/ ^int NANO_PR_SEC ^int DEFAULT_TICKS_PR_SEC)] ;; 60 fps

          (when (> ^int duration 0)
            (loop [current-nano start-nano
                   next-nano (+ current-nano sleep-nano)]
              (when (<= current-nano end-nano)
                (later
                  (doseq [[^WritableValue prop ^int start ^int end] keyvalues]
                    (.setValue prop
                       (+ start
                          (* ^double (/ (- current-nano start-nano) duration-nano)
                             (- end start))))))

                (let [sleep-milli (int (/ (- next-nano current-nano) ^int NANO_PR_MILLI))]
                  (if (> sleep-milli 0)
                    (Thread/sleep sleep-milli)))

                (recur next-nano (+ current-nano sleep-nano))))
            ;; correct final value and "hold" until to ensure consistent state at end
            (now (doseq [[^WritableValue p _ e] keyvalues]
                       (.setValue p e)))))))

;(set! *warn-on-reflection* false)
;(set! *unchecked-math* false)


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


(defn priority [kw]
  ({:always Priority/ALWAYS
    :never Priority/NEVER
    :sometimes Priority/SOMETIMES} kw))


(defn region
 "optional kwargs:
    :hgrow :always/:never/:sometimes
    :vgrow :always/:never/:sometimes"

    [& {:keys [hgrow vgrow] :or {}}]

    (doto (Region.)
        (HBox/setHgrow (priority hgrow))
        (VBox/setVgrow (priority vgrow))))


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
             (.setFill (:fill kwargs))
             (.setStroke (:stroke kwargs))
             (.setStrokeWidth (:strokewidth kwargs)))))


(defn node? [item]
  (instance? Node item))

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
          (.setFill (:fill kwargs))
          (.setArcWidth arc)
          (.setArcHeight arc))))


(defn ^Label label
    ([] (Label.))
    ([text] (Label. text))
    ([text node] (Label. text node)))


(defn set-tooltip [control s]
  (.setTooltip control (Tooltip. s))
  control)


(defn set-onaction [buttonbase fn-or-handler]
  (.setOnAction buttonbase (ensure-handler fn-or-handler))
  buttonbase)


(defn set-onmouseclicked [clickable fn-or-handler]
  (.setOnMouseClicked clickable (ensure-handler fn-or-handler))
  clickable)


(defn ^Button button [label & {:keys [onaction width minwidth tooltip]}]
    (let [b (Button. label)]
        (if width (.setPrefWidth  b (double width)))
        (if minwidth (.setMinWidth b  (double minwidth)))
        (if onaction (set-onaction b onaction))
        (if tooltip (set-tooltip b tooltip))
        b))


(defn ^CheckBox checkbox [label & {:keys [onaction tooltip]}]
  (let [cb (CheckBox. label)]
    (when onaction (.setOnAction cb (event-handler (onaction))))
    (when tooltip (.setTooltip cb (Tooltip. tooltip)))
    cb))


(def font-postures
  {:regular FontPosture/REGULAR
   :italic  FontPosture/ITALIC})


(def font-weights
  {:normal  FontWeight/NORMAL
   :medium FontWeight/MEDIUM
   :semibold FontWeight/SEMI_BOLD})


(defn new-font
 ([family-or-size]
  (if (string? family-or-size)
     (Font/font ^String family-or-size)
     (Font/font (double family-or-size))))
 ([family size]
  (Font/font family (double size)))
 ([family weight size]
  (Font/font ^String family ^FontWeight (font-weights weight) (double size)))
 ([family weight posture size]
  (Font/font family (font-weights weight) (font-postures posture) (double size))))


(defn set-font
 ([item font-or-family-or-size]
  (if (instance? Font font-or-family-or-size)
    (.setFont item font-or-family-or-size)
    (set-font item (new-font font-or-family-or-size))))
 ([item family size]
  (set-font item (new-font family size))))


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
      (when font (set-font ta font))
      ta))


(defn text [s & {:keys [font size color]
                   :or {size  12
                        color Color/BLACK}}]
  (doto (Text. s)
    (.setFill color)
    (set-font (or font (new-font size)))))


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
                    :alignment nil
                    :background nil})
          box
          (doto (if vertical?
                    (VBox. (:spacing kwargs) (fxj/vargs-t* Node nodes))
                    (HBox. (:spacing kwargs) (fxj/vargs-t* Node nodes)))
              (BorderPane/setMargin (insets (:insets kwargs)))
              (.setAlignment  (:alignment kwargs))
              (.setStyle (format "-fx-padding: %s %s;" (:padding kwargs) (:padding kwargs))))]

      (when-let [b (:background kwargs)]
          (set-background box b))

      box))


(defn ^HBox hbox [& args]
    (apply box (cons false args)))


(defn ^VBox vbox [& args]
    (apply box (cons true args)))


(defn ^BorderPane borderpane
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


(defn ^Scene scene [root & args]
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


(defn centering-point-on-primary
    "returns [x y] for centering (stage) no primary screen"
    [scene-or-stage]
    (let [prim-bounds (.getVisualBounds (Screen/getPrimary))]
        [ (-> prim-bounds .getWidth (/ 2) (- (/ (.getWidth scene-or-stage ) 2)))
          (-> prim-bounds .getHeight (/ 2) (- (/ (.getHeight scene-or-stage ) 2)))]))


(defn imageview [rsc-str]
    (ImageView. (Image. rsc-str)))


(defn screens []
    (Screen/getScreens))


(defn ^Screen primary-screen []
    (Screen/getPrimary))


(defn stagestyle [style-kw]
    (get {:decorated StageStyle/DECORATED
          :transparent StageStyle/TRANSPARENT
          :undecorated StageStyle/UNDECORATED
          :unified StageStyle/UNIFIED
          :utility StageStyle/UTILITY}
         style-kw
         StageStyle/DECORATED))


(defn side [side-kw]
  (get {:top Side/TOP
        :bottom Side/BOTTOM
        :left Side/LEFT
        :right Side/RIGHT}
       side-kw
       Side/BOTTOM))


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
           :alwaysontop false
           :tofront false
           :resizable true
           :oncloserequest #()  ;; good for preventing closing (consume event)
           :onhiding #()  ;; good for saving content
           :onhidden #()}  ;; good for removing references to self

          [_ kwargs] (fxu/partition-args args default-kwargs)]

      (let [stg (doto (Stage. (stagestyle (:style kwargs)))
                    (.setTitle (:title kwargs))
                    (.setScene (:scene kwargs))
                    (.setAlwaysOnTop (:alwaysontop kwargs))
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
          (when (:tofront kwargs) (.toFront stg))

          stg)))



(defn filechooserfilter [description & extensions]
    (FileChooser$ExtensionFilter. description (fxj/vargs* extensions)))


(def FILESCHOOSER_FILTERS_CLJ [
                               (filechooserfilter "Clojure Files" "*.clj")
                               (filechooserfilter "All Files"   "*.*")])

(def FILESCHOOSER_FILTERS_PNG [
                               (filechooserfilter "PNG files" "*.png")
                               (filechooserfilter "All Files"   "*.*")])


(defn ^FileChooser filechooser [& filters]
    (doto (FileChooser.)
        (-> .getExtensionFilters (.addAll (fxj/vargs* filters)))))


(def sample-codes-map {
                       #{:S} #(println "S")
                       #{:S :SHIFT} #(println "SHIFT-S")
                       #{:S :SHORTCUT} #(println "CTRL-S")
                       #{:S :ALT} #(println "ALT-S")
                       #{:S :SHIFT :SHORTCUT} (event-handler (println "SHIFT-CTRL/CMD-S"))
                       #{:SHORTCUT :ENTER} (event-handler-2 [_ event] (println "CTRL/CMD-ENTER") (.consume event))})


(def sample-chars-map {
                       "a" #(println "a")
                       "A" #(println "A")
                       " " (event-handler-2 [_ e] (println "SPACE (consumed)") (.consume e))})


;; TODO make macro that does this:
;; (condmemcall true (toUpperCase))
;;  => (fn [inst] (if true (.inst  (toUpperCase)) inst)
;; test:
;; (doto "A" .toLowerCase (fn [inst] (if (= 1 2) (.inst  (toUpperCase)) inst)))


(defn key-pressed-handler
 "Takes a map where the key is a set of keywords and the value is a no-arg function to be run or an instance of EventHandler.

The keywords int the set must be uppercase and correspond to the constants of javafx.scene.input.KeyCode.
Use :SHIFT :SHORTCUT :ALT for platform-independent handling of these modifiers (CTRL maps to Command on Mac).
If the value is a function, then it will be run, and then the event will be consumed.
If the value is an EventHandler, then it will be called with the same args as this handler, and it must itself consume the event if required.

Example of codes-map:
{   #{:S}              #(println \"S\")  ;; event consumed
    #{:S :SHIFT}       #(println \"SHIFT-S\")
    #{:S :SHIFT :SHORTCUT} (fx/event-handler (println \"SHIFT-CTRL/CMD-S\"))  ;; event not consumed
    #{:SHORTCUT :ENTER}    (fx/event-handler-2 [_ event] (println \"CTRL/CMD-ENTER\") (.consume event ))
    }"
    [codes-map & {:keys [handle-type consume-types]}]
    (event-handler-2
        [inst event]
        ;(println "  ## inst:" inst "  source:" (.getSource event ))
        (let [
              ev-typ (.getEventType event)
              combo (ufx/code-modifier-set event)
              ;_ (println "combo:" (str combo))
              do-handle
              #(if (instance? EventHandler %)
                   (.handle % event)
                   (do (%) (.consume event)))]

            (when-let [f (codes-map combo)]
              ;(println "  ## f:" f)
              (if handle-type
                (if (= handle-type ev-typ)
                  (do-handle f))
                (do-handle f))
              ;(println "  ## ev-typ:" ev-typ)
              ;(println "  ## consume-types:" consume-types)
              (when (and consume-types ((set consume-types) ev-typ))
                ;(println "  ## consuming:" ev-typ)
                (.consume event))))))


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
        (let [ch-str (.getCharacter event)]
            (when-let [v (chars-map ch-str)]
                (if (instance? EventHandler v)
                    (.handle v event)
                    (v))))))

