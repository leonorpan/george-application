(ns george.app.turtle.turtle

    "George Turtle Geometry implements only the basic (procedural) single-turtle  functions of the original UCB Logo TG, not (for now) the obect-oriented multi-turtle environment of Python.

Furthermore, certain changes have been made to some of the function-names to make them more consistent, readable, an 'clojure-y' - mainly inserting hifens in multy-word function names.

We use 'standard' mode for TG as this is most in line with underlying standard math:
1. Turtle home is origo.
2. Origo is center of screen.
3. X increases to the right, Y increases upwards.
4. Degrees are counter-clocwise. 0 degrees is facing right.

    "


    (:require [george.javafx :as fx]
              [george.javafx.util :as fxu])
    (:import (javafx.scene.paint Color)
             (javafx.scene.canvas Canvas)
             (javafx.scene Node Group)
             (javafx.scene.transform Rotate)
             (javafx.scene.shape Polygon)))



(definterface IScreen)



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
(definterface ITurtle
    (sayHello [])
    (left [degrees])
    (right [degrees])
    (forward [distance])
    (getX [])
    (getY [])
    (getHeading [])
    (setHeading [angle])
    (setXY[x y])
    (isPenDown [])
    (setPenDown [bool])
    (getPenColor [])
    (setPenColor [color]))


(defn- get-heading* [inst]
  (let [a (- (rem (.getRotate inst) 360.0))]
    a))



(defn- set-heading* [inst ang]
  (let [diff (- (get-heading* inst) ang)
        duration (* (/ (Math/abs diff) (* 3 360.)) 1000)]
    (fx/synced-keyframe
           duration ;; 3 rotations pr second
           [(.rotateProperty inst) (- ang)])))



(defn- normalize-angle [a]
    (cond
        (zero? a) 0.0
        (< 0.0 a 360.0) a
        :default (rem a 360)))



(defn- get-x* [inst]
    (let [x (.getTranslateX inst)]
        x))

(defn- get-y* [inst]
    (let [y (- (.getTranslateY inst))]
        ;(println "  ## get-y*" y)
        y))

(declare screen)

(defn- add-node [screen node]
    (fx/now (fx/add (-> screen .getUserData :root) node)))


(defn- set-xy* [inst target-x target-y]
    (let []

      (fx/synced-keyframe
         250
         [(.translateXProperty inst) target-x]
         [(.translateYProperty inst) (- target-y)])))


(defn- do-forward* [inst dist]
  (let [ang (get-heading* inst)
        x (get-x* inst)
        y (get-y* inst)
        [x-fac y-fac] (fxu/degrees->xy-factor ang)
        target-x (+ x (* dist x-fac))
        target-y (+ y (* dist y-fac))
        line
        (when (.isPenDown inst)
              (fx/line :x1 x :y1 (- y) :color (Color/web (.getPenColor inst))))]

    (when line
        (add-node (screen) line)
        (fx/later (.toFront inst)))

    (fx/synced-keyframe
            (* (/ (Math/abs dist) 600) 1000)  ;; 600 px per second
        [(.translateXProperty inst) target-x]
        [(.translateYProperty inst) (- target-y)]
        (when line [(.endXProperty line) target-x])
        (when line [(.endYProperty line) (- target-y)]))))


(defn- do-rotate* [inst deg]
    ;(println "  ## do-rotate* " deg)
    (let [new-angle (+ (get-heading* inst) deg)]
        (set-heading* inst new-angle)))


(defn- turtle-impl [name]
    (let [state
          (atom {:pen-down true
                 :pen-color "black"})

          poly
          (fx/polygon 5 0  -5 5  -3 0  -5 -5  :fill fx/ANTHRECITE)
          turt
          (proxy [Group ITurtle] []
            (sayHello []
                (println (format "Hello.  My name is %s.  %s the Turtle." name name)))
            (left [deg]
                ;(println "  ## left" deg)
                (do-rotate* this deg))
            (right [deg]
                ;(println "  ## right" deg)
                (do-rotate* this (- deg)))
            (forward [dist]
                ;(println "  ## forward" dist)
                (do-forward* this dist))
            (getX []
                (let [x (get-x* this)]
                    ;(println "  ## getX" x)
                    x))
            (getY []
                (let [y (get-y* this)]
                    ;(println "  ## getY" y)
                    y))
            (getHeading []
                (let [a  (get-heading* this)]
                    ;(println "  ## getHeading" a)
                    a))
            (setHeading [angle]
                ;(println "  ## setHeading" angle)
                (set-heading* this angle))
            (setXY [x y]
                ;(println "  ## setXY" x y)
                (set-xy* this x y))


            (isPenDown []
                (:pen-down @state))
            (setPenDown [b]
                ;; TODO: assert boolean type
                (swap! state assoc :pen-down b))
            (getPenColor []
                (:pen-color @state))
            (setPenColor [color]
                ;; TODO: assert color one of web(str), key (in pallette), javafx.paint.Color
                (swap! state assoc :pen-color color)))]

        (fx/add turt poly)

        turt))



(defn- create-turtle []
    (doto (turtle-impl "Tom") .sayHello))

#_(defn- get-create-resize-screen
    "Returns existing singleton screen and brings it to front.
    If one doesn't already exist, then a new one is created.
    If the size of the existing screen doesn't match, it will be resized.
    "
    [w h])


;; TODO: implement CRUD ref. spec
(defonce  workspace (atom {}))

;(def ^:private screen-singleton (atom nil))
;(def ^:private turtle-singleton (atom nil))
(def ^:private screen-and-turtle-singleton (atom nil))





(defn- create-screen [w h]
    (fx/now
        (let [
              root (fx/group)
              ;; rotation about X or Y axis not pissible on machines without SCENE3D
              ;; (AKA very small cheap school laptops)
              ;_ (.setRotationAxis root Rotate/X_AXIS)
              ;_ (.setRotate root 180)

              ;origo (fx/rectangle :location [-1 -1] :size [3 3] :fill fx/RED)
              ;_ (fx/add root origo)

              ;up-and-over (fx/rectangle :location [20 20] :size [3 3] :fill fx/BLACK)
              ;_ (fx/add root up-and-over)

              stage (fx/stage
                        :title "Turtle Geometry"
                        :scene (fx/scene root :size [w h] :fill fx/WHITESMOKE)
                        :resizable true
                        :location [30 120]
                        :onhidden #(reset! screen-and-turtle-singleton nil))]

          ;; not useful to bind now, as resizable is false
          (doto root
            (-> .layoutXProperty (.bind (-> stage .widthProperty (.divide 2))))
            (-> .layoutYProperty (.bind (-> stage .heightProperty (.divide 2)))))

          (.setUserData stage {:root root})
          stage)))



(comment defn- get-or-create-screen [w h]
    (if-let [scrn @screen-singleton]
        (fx/now (doto scrn (.toFront)))
        (reset! screen-singleton (create-screen w h))))


;(defn- create-turtle []
;    (fx/polygon 5 0  -5 5  -3 0  -5 -5  :fill fx/ANTHRECITE))


(def DEFAULT_SCREEN_SIZE [600 450])


(comment defn- get-or-create-turtle []
    (if-let [turt @turtle-singleton]
        turt
        (let [t (reset! turtle-singleton (create-turtle))]
            (fx/later
                (fx/add
                    (-> (apply get-or-create-screen DEFAULT_SCREEN_SIZE) .getUserData :root) t))
            (Thread/sleep 1000))))



(defn- get-or-create-screen-and-turtle
    ([]
     (apply get-or-create-screen-and-turtle DEFAULT_SCREEN_SIZE))
    ([w h]
     (if-let [s-n-t @screen-and-turtle-singleton]
         (do
             (fx/later (.toFront (:screen s-n-t)))
             s-n-t)
         (let [scrn (create-screen w h)
               trtl (create-turtle)]
             (add-node scrn trtl)
             (Thread/sleep 1000)
             (reset! screen-and-turtle-singleton {:screen scrn :turtle trtl})))))


;;; "public" ;;;



(defn screen
  "same as 'turtle', but with possibility to create different sized screen"
    ([]
     (apply screen DEFAULT_SCREEN_SIZE))

    ([width height]
     (:screen (get-or-create-screen-and-turtle width height))))


(defn turtle
    "get-or-create a screen (with default size) and a turtle.
Returns turtle instance"
    []
    (:turtle (get-or-create-screen-and-turtle)))



(defn left [degrees]
    (.left (turtle) degrees))

(defn right [degrees]
    (.right (turtle) degrees))

(defn forward [distance]
    (.forward (turtle) distance))

(defn get-heading []
    (.getHeading (turtle)))

(defn set-heading [angle]
    (.setHeading (turtle) angle))

(defn set-xy [x y]
  (.setXY (turtle) x y))

(defn set-position [pos]
    (apply set-xy pos))

(defn pen-up []
    (.setPenDown (turtle) false))

(defn pen-down []
    (.setPenDown (turtle) true))

(defn is-pen-down []
    (.isPenDown (turtle)))

(defn get-pen-color []
    (.getPenColor (turtle)))

(defn set-pen-color [color]
    (.setPenColor (turtle) color))

(defn show []
  (.setVisible (turtle) true))

(defn hide []
  (.setVisible (turtle) false))

(defn is-showing []
    (.isVisible (turtle)))

(defn clear []
    (let [root (-> (screen) .getUserData :root)
          t (turtle)
          filtered (filter #(= % t) (.getChildren root))]
        (fx/later (-> root .getChildren (.setAll filtered)))))


(defn home []
    (show)
    (pen-up)
    (set-heading 0)
    (pen-down)
    (set-position [0 0]))


(defn reset []
    (clear)
    (home)
    (set-pen-color "black"))





(defn -main [& _]
    (fx/later (turtle)))


;;; DEV ;;;
;(println "WARNING: Running george.app.turtle.turtle/-main" (-main))

(comment do
  (println "heading:" (get-heading))
  (left 60)
  (right 30)
  (left 45)
  (println "heading:" (get-heading))
  (set-heading 120)
  (println "heading:" (get-heading))
  (forward 30)
  (pen-up)
  (right 60)
  (forward 30)
  (pen-down)
  (Thread/sleep 1000)
  (set-pen-color "red")
  (println "pen color:" (get-pen-color))
  (forward 30)

  (Thread/sleep 1000)

  (reset)

  (defn square []
      (dotimes [_ 4]
          (forward 50)
          (left 90)))

  ;(pen-up)
  (set-xy -75 -120)
  (left 90)
  ;(pen-down)

  (dotimes [_ 6]
      (square)
      (pen-up)
      (right 45)
      (forward 20)
      (left 45)
      (pen-down))

  (hide))