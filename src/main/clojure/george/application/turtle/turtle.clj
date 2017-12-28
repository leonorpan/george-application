;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.turtle.turtle

    "George Turtle Geometry implements only the basic (procedural) single-turtle  functions of the original UCB Logo TG, not (for now) the obect-oriented multi-turtle environment of Python.

Furthermore, certain changes have been made to some of the function-names to make them more consistent, readable, an 'clojure-y' - mainly inserting hifens in multy-word function names.

We use 'standard' mode for TG as this is most in line with underlying standard math:
1. Turtle home is origo.
2. Origo is center of screen.
3. X increases to the right, Y increases upwards.
4. Degrees are counter-clocwise. 0 degrees is facing right.

    "

  (:require [george.javafx :as fx]
            [george.javafx.util :as fxu]
            [clojure.java.io :as cio]
            [clojure.string :as cs]
            [george.javafx.java :as fxj])
  (:import (javafx.scene.paint Color)
           (javafx.scene.canvas Canvas)
           (javafx.scene Node Group)
           (javafx.scene.transform Rotate)
           (javafx.scene.shape Polygon Line)
           (javafx.scene.image WritableImage ImageView Image)
           (java.awt.image BufferedImage)
           (javafx.embed.swing SwingFXUtils)
           (javax.imageio ImageIO)
           (javafx.scene.input Clipboard ClipboardContent DataFormat)
           (java.io File FilenameFilter ByteArrayOutputStream)
           (java.net URI)
           (java.nio ByteBuffer)
           (javafx.scene.control ContextMenu MenuItem)
           (javafx.stage Stage)))

"UCB Logo commands (to be) implemented:
(ref: https://people.eecs.berkeley.edu/~bh/usermanual )

;; MOTION:
forward / fd <dist>  ;; <num>
back / bk <dist>  ;; <num>
left / lf <deg>  ;; <num>
right / rt <deg>  ;; <num>
set-pos <pos> ;; [<num> <num>]
set-xy  <num> <num>  ;; similar to set-pos
set-x  <num>
set-y  <num>
set-heading / set-h <deg>  ;; <num>
home  ;; = (do (set-xy 0 0)  (set-h 0))
arc  <angle> <radius>  ;; draws an arc of a circle, with the turtle at the center, with specified radius, starting at the turtle's heading and extending counter-clockwise through the specified angle.  The turtle does not move.

;; MOTION QUERIES:
pos  ;; returns [x y]
x  ;; returns just the x-coordinate
y
heading  ;; returns the heading in degrees such that: 0 <= heading < 360
towards <pos>  ;; returns what heading the turtle should be facing to point to <pos> from its current position
distance <pos>  ;; returns what <dist> the turtle needs to travel to reach <pos> from current position

;; TURTLE AND WINDOW CONTROLS:
show / show-turtle / st
hide / hide-turtle / ht
clear ;; clean
reset ;; clear-screen / cs
wrap ;; default mode. Turtle moving off window will wrap around to oposite side
window ;; Turtle can move off-screen to infity.  Do 'home' to recover.
fence ;; Turtle stops at window edge (, and an error message will possibly be emitted).
begin-fill
end-fill  ;; whatever points the turtle moved through will between these two, will be filled with whatever the color was before 'begin-fill'
; TODO: test Python-implementation for details

label <text> ;; prints Text using current color and font (typeface, style, weight, size) at turtle position according to standard JavaFX Canvas rules.
; TODO: considder also adding 'write' as in Python

;; PEN CONTROLS:
pen-down / pd
pen-up / pu
pen-color <color>  ;; 'color' can be one of html-color-string, TG-color-key, or JavaFX Color-instance.
pen-size  <num>  ;; how wide the lines should be
pen-fill  <color> ;; what color to use for filling between 'begin-fill' and 'end-fill'

pen-paint / pp  ;; set pen down and mode to :paint
pen-erase / pe  ;; sets pen down and mode to :erase
pen-reverse /px  ;; sets pen down and mode to :reverse  (TODO: investigate how this should actually work!)
set-backround / set-bg <color>

;; PEN QUERIES:
pen-down?
pen-mode?  ;; returns one of :wrap :window :fence
pen-color?
pen-size?
print-pen  ;; prints the settings of the pen\nprint-colors ;; prints a list of available TG colors \n
print-palette
background?  ;; returns the background <color>

;; WORKSPACE
(This is stores and retrieves values  'global' space for the user - implemented as a Clojure atom called workspace)
;; CRUD:

create <key> <value>  ;; returns 'nil'
read  ;;  returns a map of all content
read <key>  ;; returns a single value, else 'nil'
read <key> <not-found>  ;; reutrns a single value, else <not-found>
update <key> <value>  ;; throws Exception if key not found.  Use 'create' for 'insert or update functionality'
delete <key>  ;; returns nil whether key existed or not
delete <key> <not-found>  ;; returns <not-found> if didn't exist
"


;; avoiding reflection is A LOT faster!
;(set! *warn-on-reflection* true)
;; primitive math is faster
;(set! *unchecked-math* :warn-on-boxed)


(declare screen)

(def DEFAULT_SCREEN_SIZE [600 450])


(definterface IScreen)


(definterface ITurtle
    (sayHello [])
    (left [degrees])
    (right [degrees])
    (forward [distance])
    (getHeading [])
    (setHeading [angle])
    (getPosition [])
    (setPosition [pos])
    (getPenDown [])
    (setPenDown [bool])
    (getPenColor [])
    (setPenColor [color])
    (getSpeed [])
    (setSpeed [number]))



(defn- heading* [^Group inst]
  (let [a (- (rem (.getRotate inst) 360.0))]
    a))


(defn- set-heading* [inst ^double ang]
  ;(println "  ## set-heading* " ang)
  (.setRotate ^Group inst (- ang)))


(defn- position* [^Group inst]
  [^double (.getTranslateX inst)  (- ^double (.getTranslateY inst))])


(defn- add-node [^Stage screen node]
    (fx/now (fx/add (-> screen .getUserData :root) node)))


(defn- set-position* [^Group inst [^double target-x ^double target-y]]
    (let []
      (fx/synced-keyframe
         250
         (when target-x [(.translateXProperty inst) target-x])
         (when target-y [(.translateYProperty inst) (- target-y)])))
    inst)


(defn- do-forward* [inst ^double dist]
  ;(println "  ## do-forward*" dist)
  (let [ang (heading* inst)
        [^double x ^double y] (position* inst)
        [^double x-fac ^double y-fac] (fxu/degrees->xy-factor ang)
        target-x (+ x (* dist x-fac))
        target-y (-  (+ y (* dist y-fac)))
        line
        (when (.getPenDown inst)
              (fx/line :x1 x :y1  (- y)
                       :color
                       (let [c (.getPenColor inst)]
                         (if (instance? String c)
                             (Color/web c)
                             c))))
        duration
        (when-let [speed (.getSpeed inst)]
          ;; 500 px per second is "default"
          (* (/ (Math/abs (double dist))
                (* 500. (double speed)))
             1000.))
        ;_ (println "  ## duration:" duration)

        ;; A duration less than 1.0 may result in no rendering, so we set it to nil.
        duration (if (and duration (< duration 1.)) nil duration)]
        ;_ (println "  ## duration:" duration)

    (when line
        (add-node (screen) line)
        (fx/later (.toFront ^Group inst)))

    (if duration
      (fx/synced-keyframe
        duration
        [(.translateXProperty ^Group inst) target-x]
        [(.translateYProperty ^Group inst) target-y]
        (when line [(.endXProperty ^Line line) target-x])
        (when line [(.endYProperty ^Line line) target-y]))
      (do
        (doto ^Group inst
          (.setTranslateX target-x)
          (.setTranslateY target-y))
        (when line
          (doto ^Line line
            (.setEndX target-x)
            (.setEndY target-y)))))))


(defn- do-rotate* [inst ^double deg]
    ;(println "  ## do-rotate* " deg)
    (let [new-angle (+ ^double (heading* inst) deg)]
        (set-heading* inst new-angle)))


(defn- set-speed* [state number]
  (when (and number (not (instance? Number number)))
    (throw
      (IllegalArgumentException.
        (format "set-speed requires a number or 'nil'. Got %s" number))))

  (swap! state assoc
               :speed number))


(defn turtle-polygon []
  (fx/polygon 5 0  -5 5  -3 0  -5 -5  :fill fx/ANTHRECITE))


(defn- turtle-impl [name]
    (let [state
          (atom {:pen-down true
                 :pen-color "black"})
          _ (set-speed* state 1)

          poly
          (turtle-polygon)

          turt
          (proxy [Group ITurtle] []
            (sayHello []
                (println (format "Hello.  My name is %s.  %s the Turtle." name name)))
            (left [deg]
                ;(println "  ## left" deg)
                (do-rotate* this deg))
            (right [^double deg]
                ;(println "  ## right" deg)
                (do-rotate* this (- deg)))
            (forward [dist]
                ;(println "  ## forward" dist)
                (do-forward* this dist))
            (getHeading []
                (let [a  (heading* this)]
                    ;(println "  ## getHeading" a)
                    a))
            (setHeading [angle]
                ;(println "  ## setHeading" angle)
                (set-heading* this angle))

            (getPosition []
              (let [p (position* this)]
                ;(println "  ## getPosition" p)
                p))
            (setPosition [[x y]]
                ;(println "  ## setXY" x y)
                (set-position* this [x y]))

            (getPenDown []
                (:pen-down @state))
            (setPenDown [b]
                ;; TODO: assert boolean type
                (swap! state assoc :pen-down b))

            (getPenColor []
                (:pen-color @state))
            (setPenColor [color]
                ;; TODO: assert color one of web(str), key (in pallette), javafx.paint.Color
                ;; TODO: calculate a JavaFX Color and store calculated result
                (swap! state assoc :pen-color color))

            (getSpeed []
              (:speed @state))
            (setSpeed [number]
              (set-speed* state number)))]

        (fx/add turt poly)

        turt))


(defn- create-turtle []
    (doto (turtle-impl "Tom") .sayHello))


;; TODO: implement CRUD ref. spec
(defonce  data (atom {}))

;(def ^:private screen-singleton (atom nil))
;(def ^:private turtle-singleton (atom nil))
(defonce ^:private screen-and-turtle-singleton (atom nil))


(declare copy-screenshot-to-clipboard)
(declare save-screenshot-to-file)

(defn- create-screen [w h]
    (fx/now
        (let [
              root ^Group (fx/group)
              ;; rotation about X or Y axis not possible on machines without SCENE3D
              ;; (AKA very small cheap school laptops)
              ;_ (.setRotationAxis root Rotate/X_AXIS)
              ;_ (.setRotate root 180)

              ;origo (fx/rectangle :location [-1 -1] :size [3 3] :fill fx/RED)
              ;_ (fx/add root origo)

              ;up-and-over (fx/rectangle :location [20 20] :size [3 3] :fill fx/BLACK)
              ;_ (fx/add root up-and-over)

              cm
              (ContextMenu.
                (fxj/vargs
                  (doto (MenuItem. "Copy screenshot to clipboard")
                    (.setOnAction (fx/event-handler (copy-screenshot-to-clipboard))))
                  (doto (MenuItem. "Save screenshot to file ...")
                    (.setOnAction (fx/event-handler (save-screenshot-to-file))))))

              cm-handler
              (fx/event-handler-2 [_ e]
                                  (.show cm root (.getScreenX e) (.getScreenY e)))

              stage ^Stage
                (fx/stage
                        :title "Turtle Screen"
                        :scene (doto
                                 (fx/scene root :size [w h] :fill fx/WHITESMOKE)
                                 (.setOnContextMenuRequested cm-handler))
                        :resizable true
                        :location [90 100]
                        :tofront true
                        :alwaysontop true
                        :onhidden #(reset! screen-and-turtle-singleton nil))]

          ;; not useful to bind now, as resizable is false
          (doto root
            (-> .layoutXProperty (.bind (-> stage .widthProperty (.divide 2))))
            (-> .layoutYProperty (.bind (-> stage .heightProperty (.divide 2)))))

          (.setUserData stage {:root root})
          stage)))


(defn- get-or-create-screen-and-turtle
    ([]
     (apply get-or-create-screen-and-turtle DEFAULT_SCREEN_SIZE))
    ([w h]
     (if-let [s-n-t @screen-and-turtle-singleton]
         (let [screen ^Stage (:screen s-n-t)]
             (when (.isIconified screen)
               (fx/later
                 (doto screen
                   (.setIconified false)
                   (.toFront))))
             s-n-t)
         (let [scrn (create-screen w h)
               trtl (create-turtle)]
             (add-node scrn trtl)
             (Thread/sleep 500)
             (reset! screen-and-turtle-singleton {:screen scrn :turtle trtl})))))


;;; "public" ;;;



(defn ^Stage screen
  "same as 'turtle', but with possibility to create different sized screen."
    ([]
     (apply screen DEFAULT_SCREEN_SIZE))

    ([width height]
     (:screen (get-or-create-screen-and-turtle width height))))


(defn turtle
    "get-or-create a screen (with default size) and a turtle.
Returns turtle instance"
    []
    (:turtle (get-or-create-screen-and-turtle)))


(defn left
  "Turtle command.
  Rotates the turtle counter-clockwise from current heading.
  Negative number also possible, which will result in a clockwise rotation.

  Ex.: (left 90)"
  [degrees]
  (.left  (turtle) degrees)
  nil)


(defn right
  "Turtle command.
  Does the same as as 'left', but in opposite direction."
  [degrees]
  (.right  (turtle) degrees)
  nil)


(defn forward
  "Tutle command.
  Moves the turtle forward 'distance' in the direction the turtle is heading.
  Negative number also possible, which results in the turtle moving backward.

  Ex.: (forward 50)"
  [distance]
  (.forward  (turtle) distance)
  nil)


(defn heading
  "Turtle command.
  Returns the current heading (angle) of the turtle relative to positive X axis, rotation counter-clockwise.
  The returned number will be a positive number from 0 to 359.

  Ex.: (heading)"
  []
  (.getHeading  (turtle))
  nil)


(defn set-heading
  "Turtle command.
  Rotates the turtle to the given heading.  See 'heading'.

  Ex.: (set-heading 90)"
  [degrees]
  (.setHeading  (turtle) degrees)
  nil)


(defn position
  "Turtle command.
  Returns the current position (coordinates) of the turtle relative to 'origo' (center of screen.) 'x' is right, 'y' is up.

  The value is a 2-item vector: [x y]
  The vector can be desctructed, or 'first' or 'second' can be called on it to get just the x or y value.

  Ex.:
  (position)
  (let [p (position)] ...)
  (let [[x y] (position)] ...) ;; destructing
  (let [x (first (position))] ...)
  "
  []
  (.getPosition (turtle)))


(defn set-position
  "Turtle command.
  Moves the turtle to the given position.  (See 'position').
  If x or y are \"falsy\" (i.e. 'nil' or 'false'), then that part is ignored.

  Ex.:
  (set-position [30 40])
  (set-position [30 nil]) ;; y is changed, only x"
  [[x y]]
  (.setPosition  (turtle) [x y])
  nil)


(defn pen-up
  "Pen command.
  Picks up the pen, so when the turtle moves, no line will be drawn.

  Ex.: (pen-up)"
  []
  (.setPenDown  (turtle) false)
  nil)


(defn pen-down
  "Pen command.
  Sets down the pen, so when the turtle moves, a line will be drawn.

  Ex.: (pen-down)"
  []
  (.setPenDown  (turtle) true)
  nil)


(defn get-pen-down
  "Pen command.
  Returns 'true' or 'false'.

  Ex.: (is-pen-down)"
  []
  (.getPenDown (turtle)))


(defn speed
  "Turtle command.
  Returns a number or 'nil'.

  Ex.: (speed)"
  []
  (.getSpeed (turtle)))


(defn set-speed
  "Turtle command.
  Sets the speed of the turtle to a number or 'nil'.
  The number is multiples of \"standard speed\".

  1 or 1.0 is default/standard speed
  2 or 2.0 is double speed
  0.5 or 1/2 is half speed
  etc.

  'nil' is no speed, i.e. no animations -> as fast as possible.
  A speed over 100 has no increase effect.

  A speed set such that the animation duration is less than 1, results in speed 'nil' being used.
  Ex.: (set-speed 1)"
  [number]
  (.setSpeed (turtle) number)
  nil)


(defn pen-color
  "DEPRECATED: Use 'color' instead.
   Pen command.
   Returns a string representing a \"web color\", or a JavaFX Color instance,
   e.g. \"black\" or \"#ff0000\" (red) or
     Color/CORNFLOWERBLUE or (Color/color 0 255 0 0).

   Ex.: (pen-color)

   For more on JavaFX Color, see:
   http://docs.oracle.com/javase/8/javafx/api/javafx/scene/paint/Color.html
 "
  []
  (println "WARNING: 'pen-color' is deprecated. Use 'color' instead")
  (.getPenColor (turtle)))


(defn color
  "Pen command.
  Returns a string representing a \"web color\", or a JavaFX Color instance,
  e.g. \"black\" or \"#ff0000\" (red) or
    Color/CORNFLOWERBLUE or (Color/color 0 255 0 0).

  Ex.: (pen-color)

  For more on JavaFX Color, see:
  http://docs.oracle.com/javase/8/javafx/api/javafx/scene/paint/Color.html
"
  []
  (.getPenColor (turtle)))


(defn set-pen-color
  "DEPRECATED: Use 'set-color' instead.

  Pen command.
  Sets the pen to the specified web color (String) or JavaFX Color.
  See: 'pen-color'"
  [color]
  (println "WARNING: 'set-pen-color' is deprecated. Use 'set-color' instead")
  (.setPenColor (turtle) color)
  nil)


(defn set-color
  "Pen command.
  Sets the pen to the specified web color (String) or JavaFX Color.
  See: 'pen-color'"
  [color]
  (.setPenColor (turtle) color)
  nil)


(defn show
  "Turtle command.
  Makes the turtle visible.

  Ex. (show)"
  []
  (.setVisible ^Group (turtle) true)
  nil)


(defn hide
  "Turtle command.

  Ex.: (hide)"
  []
  (.setVisible ^Group (turtle) false)
  nil)


(defn is-showing
  "Turtle command.
  Returns 'true' or 'false'.

  Ex.: (is-showing)"
  []
  (.isVisible ^Group (turtle)))


(defn clear
  "Removes all graphics from screen.

  Ex.: (clear)"
  []
  (let [sc (screen)

        ;; A hack to force the scene to re-render/refresh everything.
        [^double w ^double h] (fx/WH sc)
        _ (.setWidth sc (inc w))
        _ (.setHeight sc (inc h))
        _ (.setWidth sc  w)
        _ (.setHeight sc h)

        root (->  sc .getUserData :root)
        t (turtle)
        filtered (filter #(= % t) (fx/children root))]

      (fx/later
        (fx/children-set-all root filtered)))
  nil)


(defn home
  "Turtle command.
  Moves the turte back to the center of the screen.
  Makes the turtle visible, heading 0, postion [0 0], pen-down.

  Ex.: (home)"
  []
  (show)
  (pen-up)
  (set-heading 0)
  (set-position [0 0])
  (pen-down)
  nil)


(defn reset
  "Combined screen and turtle command.
  Clears the screen, and center the current turtle, leaving only one turtle.
  Same as calling `(clear) (home)`

  Ex.: (reset)"
  []
  (clear)
  (set-speed 1)
  (set-color "black")
  (home)
  nil)


(defn sleep
  "Utility command.
  Causes the thread to \"sleep\" for the number of milliseconds.
  1 second = 1000 milliseconds

  Ex.: (wait 2000)  ;; sleep for 2 seconds"
  [milliseconds]
  (Thread/sleep milliseconds))


;(defmacro rep [n & body]
;    `(dotimes [~'_ ~n]
;       ~@body))

(defmacro rep
  "Utility command.
  Repeatedly executes body (presumably for side-effects) from 0 through n-1.

  Ex.:
  (rep 3
       (forward 50) (left 120))"
  [n & body]
  `(let [n# (try (clojure.lang.RT/longCast ~n)
                 (catch Exception ~'e
                   (throw (IllegalArgumentException.
                            (format "First argument to `rep` must be a number. Cannot convert '%s' to number." ~n)
                            ~'e))))]
     (dotimes [~'_ n#]
       ~@body)))

;(def frm '(rep 3 (print "hello, ") (println "world")))
;(def frm '(rep "b" (print "hello, ") (println "world")))
;(def frm '(rep  (print "hello, ") (println "world")))
;(prn (macroexpand-1 frm))
;(eval frm)


(defn -main [& _]
    (fx/later (turtle)))


;;; DEV ;;;

;(println "WARNING: Running george.application.turtle.turtle/-main" (-main))

(defn run-sample
  "A test program which uses most of the available turtle commands."
  []
  (reset)
  (println "heading:" (heading))
  (left 60)
  (right 30)
  (left 45)
  (println "heading:" (heading))
  (set-heading 120)
  (println "heading:" (heading))
  (forward 30)
  (pen-up)
  (right 60)
  (forward 30)
  (pen-down)
  (Thread/sleep 1000)
  (set-color "red")
  (println "pen color:" (color))
  (forward 30)

  (sleep 2000)

  (reset)

  ;(pen-up)
  (set-position [-75 -120])
  (left 90)
  ;(pen-down)
  (set-color Color/CORNFLOWERBLUE)
  (rep 6
       (dotimes [_ 4]
         (forward 50) (left 90))
       (pen-up)
       (right 45)
       (forward 20)
       (left 45)
       (pen-down))

  (hide))


;(set! *warn-on-reflection* false)


(def SCREENSHOT_BASE_FILENAME "TG_screenshot")


(defn- parse-int [s]
  (if-let [number (re-find #"\d+" s)]
    (Integer/parseInt number)
    0))


;(defn- get-filename [file]
;  (.getName file))


;(defn- get-file-num []
;  (let [img-dir (cio/file "../images/")
;
;        filenames (into []
;                        (map get-filename
;                             (file-seq img-dir)))
;
;        biggest-number (apply max
;                              (remove nil?
;                                      (map parse-int filenames)))]
;
;    (+ biggest-number 1)))


(defn- find-next-file-numbering [dir]
  (->> (.listFiles
         (cio/file dir)
         (reify FilenameFilter
           (accept [_ _ name]
             (boolean (re-find (re-pattern SCREENSHOT_BASE_FILENAME) name)))))
       (seq)
       (map #(.getName %))
       (map parse-int)
       (remove nil?)
       (#(if (empty? %) '(0) %))
       ^long (apply max)
       (inc)))



(defn- write-image-to-file
       "Writes image to file (as '.png')"
       [image file]
       (cio/make-parents file)
       (ImageIO/write
         (SwingFXUtils/fromFXImage image nil)
         "png"
         file))


(def CB (fx/now (Clipboard/getSystemClipboard)))


(defn- print-clipboard-content
  "For dev/test purposes."
  []
  (println "  ## CB content types:" (fx/now (.getContentTypes CB)))
  ;(fx/now (.getString CB)))
  (fx/now (.getImage CB)))


(defn- write-image-to-tempfile [image]
  (let [file (File/createTempFile (str SCREENSHOT_BASE_FILENAME "_") ".png")]
    (when (write-image-to-file image file)
      file)))


(defn- image->png-bytebuffer [^WritableImage im]
  (let [baos (ByteArrayOutputStream.)
        _ (ImageIO/write (SwingFXUtils/fromFXImage im nil) "png" baos)
        res (ByteBuffer/wrap (.toByteArray (doto baos .flush)))]
    (.close baos)
    res))


(defn- put-image-on-clipboard [image text-repr]
  (let [png-mime "image/png"
        png
        (if-let [df (DataFormat/lookupMimeType png-mime)]
          df (DataFormat. (fxj/vargs png-mime)))
        tempfile
        (write-image-to-tempfile image)
        cc
        (doto (ClipboardContent.)
              (.putImage image)
              ;(.put png (image->png-bytebuffer image))  ;; doesn't work for some reason!
              (.putFiles [tempfile])
              (.putFilesByPath [(str tempfile)])
              (.putString text-repr))]
    (fx/now (.setContent CB cc))))


(defn- ^WritableImage snapshot
  ""
  [scene]
  (let [[w h] (fx/WH scene)]
    (.snapshot scene (WritableImage. w h))))


(defn- ^WritableImage screenshot []
  (fx/now (-> (screen) .getScene snapshot)))


"A fileshooser-object is instanciated once pr session. It remembers the previous location.
One might in future choose to save the 'initial directory' so as to return user to same directory across sessions."
(defonce screenshot-filechooser
  (apply fx/filechooser fx/FILESCHOOSER_FILTERS_PNG))


(defn- build-filename-suggestion [dir]
    (format "%s%s.png"
            SCREENSHOT_BASE_FILENAME
            (find-next-file-numbering dir)))


(defn- ^File choose-target-file
  "If user selects a location and a file-name, then a file object is returned. Else nil.
  (A file hasn't been created yet. Only name and location chosen. The file is created when it is written to.)"
  []
  (let [initial-dir
        (if-let [dir (.getInitialDirectory screenshot-filechooser)]
                dir
                (cio/file (System/getProperty "user.home")))

        suggested-filename
        (build-filename-suggestion initial-dir)]

    (when-let [file (-> (doto screenshot-filechooser
                          (.setTitle "Save sreenshot as ...")
                          (.setInitialFileName suggested-filename))
                        (.showSaveDialog nil))]

      ;; If a different directory has been chosen, we want the filechooser to remember it:
      (.setInitialDirectory screenshot-filechooser (.getParentFile file))
        ;; Handling of potential overwrite of file is buildt into filechooser.
      file)))


(defn- save-screenshot-to-file []
  (when-let [file (choose-target-file)]
    (write-image-to-file (screenshot) file)))


(defn- copy-screenshot-to-clipboard []
  (put-image-on-clipboard (screenshot) (format "<%s>" SCREENSHOT_BASE_FILENAME)))



(def ordered-command-list
  [#'forward
   #'left
   #'right
   #'home
   #'show
   #'hide
   #'pen-up
   #'pen-down
   #'speed
   #'set-speed
   #'color
   #'set-color
   #'clear
   #'reset

   #'heading
   #'set-heading
   #'position
   #'set-position
   #'turtle
   #'screen
   #'rep
   #'sleep
   #'run-sample])


