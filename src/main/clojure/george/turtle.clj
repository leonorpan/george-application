;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle
  ^{:author "Terje Dahl"
    :doc "George Turtle Geometry implements a hybrid API.
    It is initially based concepts and functions from the original UCB Logo TG, but it also incorporating ideas from MicroWorld, as well as Python's TG.
    Also, we add some additional functionality, such as threading support and animation support, which now is easily handled by the underlying Clojure/Java/JavaFX technology.
     
    Also, we strive for consistency in function naming and arguments patterns, and while this is Clojure, we choose not to use all Clojure-specific naming conventions:
    - We don't end predicates with '?' or conversion functions starting with '->'.  
    - We do, however use lower-case and hyphens, rather than camel-case.
     
    - We use 'set-', 'get-' and 'is-' where we are working with Turtle, Pen, or Screen properties directly. 
    - In imperative commands, we start directly with the verb - e.g. 'forward'.
     
    - We try to avoid duplicate functions, but have some for the purpose of keeping some of the traditional commands - .e.g 'set-visible' does the same as 'show'/'hide'.
    
  In general we want easy, everyday words that also non-english speaking children can learn.
  And we want the naming standard to be relatively general, so it is a bit closer to other common main-stream languages, such as Java.
    
  The geometry is \"standard mode\" for TG:  
  This way, anything about positions and angles is easily transferred to/from standard school math - aka Cartesian geometry:
  - Turtle home is origo - i.e. center of screen.
  - X increases to the right, Y increases upwards.
  - Degrees are counterclockwise. 0 degrees is facing right.
"}

  (:require
    [defprecated.core :as depr]
    [flatland.ordered.map :refer [ordered-map]]
    [george.javafx :as fx]
    [george.javafx.util :as fxu]
    [george.application.output :as output]
    [clojure.string :as cs]
    [george.turtle.aux :as aux])
  (:import
    [javafx.scene.paint Color]
    [javafx.scene Group Node Scene]
    [javafx.scene.shape Line Rectangle Polygon]
    [javafx.scene.text TextBoundsType Text]
    [javafx.stage Stage]
    [javafx.geometry VPos]
    [javafx.scene.transform Rotate]
    [javafx.animation Timeline Animation Animation$Status]
    [clojure.lang Atom]))

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


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)


(declare
  move
  turn
  get-default
  turtle
  get-color
  get-width
  set-visible_
  set-visible
  is-visible
  is-down
  is-round
  set-round
  get-speed
  set-speed
  set-heading_
  set-heading
  get-heading
  set-position_
  set-position
  get-position
  heading-to
  get-state
  get-undo
  set-node_
  get-node
  un-parent_
  set-parent_
  get-parent
  set-state
  register-turtle
  get-all-turtles
  get-screen-default
  reset
  get-size
  get-fence
  allowed-fence-values
  stop-ticker
  is-overlapping
  reset-onkey
  is-ticker-running
  set-onkey-handlers
  screen
  get-screen
  is-screen-visible
  set-screen-visible
  new-screen)
  
;; Is re-declared later
(def ^:dynamic *screen*)


;; An empty record is an easy way to type a map.
(defrecord Turtle [])
(defrecord Screen [])


(defn nil-or-number? [x]
  (or (nil? x)
      (number? x)))

(defn pos-number? [^double x]
  (and (number? x)
       (pos? x)))


(defn nil-or-not-neg-number? [x]
  (or (nil? x)
      (and (number? x)
           (not (neg? (double x))))))


(defn xy-vector?
  "Returns true if 'item' is a vector containing 2 numbers."
  [item]
  (and (vector? item)
       (= 2 (count item))
       (number? (first item))
       (number? (second item))))


(defn- add-now [parent node]
  (fx/now (fx/add parent node)))


(defn- add-later [parent node]
  (fx/later (fx/add parent node)))


(defn- set-now [parent node]
  (fx/now (fx/set-all parent node)))


(defn flip-Y
  "Takes a position [x y] and inverts y - for mapping turtle-coordinates to JavaFX coordinates."
  [[x ^double y]]
  [x (- y)])


(defn get-root
  "Returns the top-level parent for all turtle rendering."
  []
  (:root @(get-screen)))


(defn turtle?
  "Returns true if the 'turtle' is a turtle."
  [turtle]
  (and (instance? Atom turtle) (instance? Turtle @turtle)))


(defn set-undo
  "Set the undo-buffers size.
   'size' must be `0` or a positive integer.
   Smaller is better - i.e. make it only as big as you think you will need, especially when dealing with lots of turtles, as each action will grow the buffer by a complete state-map.
   `0` is default.
*Examples:*
```
(set-undo 1)  ;; will allow for 1 undo - often enough for certain uses
(set-undo 0)  ;; no undo
```"
  ([size]
   (set-undo (turtle) size))
  ([turtle size]
    ;; TODO: assert data input.  Strip buffer.  Support :default
   (swap! turtle assoc :undo size)
   nil))


(defn get-undo 
  "Returns the size limit of the undo-buffer - `0` or a positive integer."
 ([]
  (get-undo (turtle)))
 ([turtle]
  (:undo @turtle)))


(defn- append-and-trim [vec obj ^long lim]
  (let [new-vec (conj vec obj)
        l (count new-vec)]
    (if (> l lim)
      (subvec new-vec (- l lim))
      new-vec)))


;; If set to `(atom [])`, then append-undo-maybe will simply append new-nodes to the atom, and not append to the turtles undo, knowing it will be handled later from some other spot, like from `arc`.
(def ^:dynamic *undo-nodes* nil)


(defn append-undo-maybe [turtle prev-state & [new-nodes]]
  ;(println "  ## *undo-nodes*" *undo-nodes* new-nodes)
  (if-let [nodes_  *undo-nodes*]
    (swap! nodes_ #(vec ( concat % (or new-nodes []))))
    (let [size (get-undo turtle)]
      (when-not (zero? ^int size)
        ;; append
        (swap! turtle update :undo-buffer
               (fn [buf]
                   (append-and-trim 
                       buf
                       [(dissoc prev-state :undo-buffer)  ;; we don't want buffers in buffers! 
                        new-nodes]
                       (get-undo turtle))))))))


(defn- undo-peek [turtle]
  (-> @turtle :undo-buffer peek))


(defn- undo-pop
  "Returns the popped item, or 'nil' if history was empty."
  [turtle]
  (let [popped (undo-peek turtle)]
    (when popped
      (swap! turtle update :undo-buffer pop))
    popped))


(defn undo
  "sets the turtle to it's previous state, then removes that item from the history buffer.
  This action can not be undone.
  
  Returns `1` if an undo happened, else `0`.

  History does not care about 'props' set on turtle, only \"internal\" states.
  
  `undo` does not animate its movements. It is instantaneous.
  
  `undo` is currently only implemented for movements (including `write`), but not for all state-changing commands.
  (Should it be?)  
  See documentation for each command to see if it implements `undo`.
  
*Examples:*
```
(undo) 
(rep 4 (undo))  ;; do multiple undos
(loop [res (undo)] (when (= 1 res) (recur (undo)))  ;; loop until all possible undos are done
```"
  ([]
   (undo (turtle)))
  
  ([turtle]
   (if-let [popped (undo-pop turtle)]
     (let [[prev-state nodes] popped]
       (set-state turtle prev-state)
       (doseq [^Node n nodes]
         (fx/later (fx/remove (.getParent n) n)))
       1)
     0)))


(defn distance-to
  "Not a turtle command. It does not effect the turtle.
  
  Calculates and returns the distance to the [x y] target position.
  The start position can be derived from implicit or explicit turtle, or from an explicit [x y] start-position.

*Examples:*
```
(distance-to [100 100])          ;; distance from current turtle to [100 100]
(distance-to a-turtle [100 100]) ;; distance from 'a-turtle' to [100 100]
(distance-to [30 50] [100 100])  ;; distance from [30 50] to [100 100] - in this case `86.0`
```"
 ([[x y]]
  (distance-to (turtle) [x y]))
  
 ([turtle-or-xy-start-position [^double x ^double y]]
  (assert (or (turtle? turtle-or-xy-start-position) (xy-vector? turtle-or-xy-start-position))
          (format "First argument must be a turtle or a position [x y]. Got %s" turtle-or-xy-start-position))

  (let [[^double start-x ^double start-y]
        (if (turtle? turtle-or-xy-start-position)
          (get-position turtle-or-xy-start-position)
          turtle-or-xy-start-position)
        w (double (- x start-x))
        h (double (- y start-y))]
    (fxu/hypotenuse w h))))


(defn turn-to
  "Rotates the turtle towards the provided position.
  This is the underlying command for `left` and `right`.

  Implements [`undo`](var:undo).

  *Example:*
```
(turn-to [100 100])
```"
 ([[x y]]
  (turn-to (turtle) [x y]))

 ([turtle [x y]]

  (let [prev-state (get-state turtle)
        ^double heading     (get-heading turtle)
        ^double new-heading (heading-to turtle [x y])]
    (when (not= heading new-heading)
          (turn turtle (- new-heading heading)))

    (append-undo-maybe turtle prev-state))))


(defn heading-to
  "Not a turtle command. It does not effect the turtle.
  
  Calculates and returns the absolute heading (angle in degrees, Cartesian system) to the [x y] target position.
  The start position can be derived from implicit or explicit turtle, or from an explicit [x y] start-position.

*Examples:*
```
(heading-to [100 200])
(heading-to a-turtle [100 200])
(heading-to [20 50] [100 200])   ;; angle from [20 50] to [100 200] - in this case `61.93` degrees
```"
  ([[x y]]
   (heading-to (turtle) [x y]))

  ([turtle-or-xy-start-pos [^double x ^double y]]
   (assert (or (turtle? turtle-or-xy-start-pos) (xy-vector? turtle-or-xy-start-pos))
           (format "First argument must be a turtle or a position [x y]. Got %s" turtle-or-xy-start-pos))

   (let [[^double start-x ^double start-y]
         (if (turtle? turtle-or-xy-start-pos)
           (get-position turtle-or-xy-start-pos)
           turtle-or-xy-start-pos)
         w (double (- x start-x)) 
         h (double (- y start-y))]
     (mod (+ (Math/toDegrees (Math/atan2 h w))
            360) 
          360))))

;(println (heading-to [0 0] [10 10]))
;(println (heading-to [0 0] [-10 10]))
;(println (heading-to [0 0] [-10 -10]))
;(println (heading-to [0 0] [10 -10]))
;(println (heading-to [10 10] [20 100]))
;(println (heading-to [10 10] [-20 100]))
;(println (heading-to [-100 10] [20 100]))
;(println (heading-to [10 10] [-20 -100]))
;(println (heading-to [-10 -10] [-20 -100]))


(defn is-overlap
  "returns 'true' if the nodes of two turtles touch/overlap (intersect)."
  [turtle1 turtle2]
  (.intersects (.getBoundsInParent ^Group (:group @turtle1))
               (.getBoundsInParent ^Group (:group @turtle2))))


(defn get-overlappers
  "Returns a seq of turtles (or Nodes) that intersect with the implicit current turtle or explicit turtle.
   Candidate turtles are implicitly selected from `(all-turtles)`, optionally from a passed-in seq of turtles (or Nodes).
   It does not check against itself.
   "
  ([]
   (get-overlappers (turtle)))
  ([turtle]
   (get-overlappers turtle (get-all-turtles)))
  ([turtle turtles]
   (filter #(and (not= turtle %) (is-overlap turtle %))  
           turtles)))


(defn- fence-able?
  "Returns `true` if the target-pos will need to be fenced.
  This is important to know know in case fence is a function."
  [[low-x low-y] [high-x high-y]  [target-x target-y]]
  (let [clamp-x? (not (<= low-x target-x high-x))
        clamp-y? (not (<= low-y target-y high-y))] 
    (or clamp-x? clamp-y?)))


(defn- calculate-fencing
  "Takes the low and high bounds for the screen, the fence type, and the start and end positions.
  
  Returns either a 4-element vector:
  - [false stop-position nil nil] if no fencing was applied
  - [true stop-position nil nil] if :stop was applied
  - [true stop-position continue-position new-target-position] if :wrap was applied"
  
  [[^double low-x ^double low-y :as low-pos] 
   [^double high-x ^double high-y :as high-pos] 
   fence 
   [^double start-x ^double start-y :as start-pos] 
   [^double target-x ^double target-y :as target-pos]]

  (if-not (#{:stop :wrap} fence)
    [false target-pos nil nil] ;; we don't do anything
    (if-not (fence-able? low-pos high-pos target-pos)
      [false target-pos nil nil] ;; we don't do anything
      ;; else
      (let [ ;; which direction are we moving in x/y?
            direction-x-pos? (<= start-x target-x)
            direction-y-pos? (<= start-y target-y)
            ;_ (println "  ## direction-x/y-pos?" direction-x-pos? direction-y-pos?)
       
            ;; Did I stop at x or y first?
            ;; If the actual y-to-edge is bigger than if we calculate it based on x-to-edge,
            ;;  then that means we hit an x-edge first, and we should move to the opposite x-edge.
  
            ;; Get absolute heading
            heading (heading-to start-pos target-pos)
            x-to-edge-actual (if direction-x-pos? (- high-x start-x) (- low-x start-x))
            y-to-edge-actual (if direction-y-pos? (- high-y start-y) (- low-y start-y))
            y-to-edge-by-x  ( * x-to-edge-actual (Math/tan (Math/toRadians heading)))
  
            ;; A left/right edge was reached first, so shift x by +/- screen width
            shift-x? (< (Math/abs (double y-to-edge-by-x)) (Math/abs (double y-to-edge-actual))) 
            
            ;; How much of the x/y dist did we consume
            consumed-x (if shift-x?
                         x-to-edge-actual
                         (/ y-to-edge-actual (Math/tan (Math/toRadians heading))))
            consumed-y  (if shift-x?
                           y-to-edge-by-x
                           y-to-edge-actual)
            ;_ (println "  ## consumed-x/y" consumed-x consumed-y)
            
            ;; At what x/y should we stop (for now)
            ;; consumed-x/y might be negative
            stop-x (+ start-x consumed-x)
            stop-y (+ start-y consumed-y)]
            ;_ (println "  ## stop-x/y" stop-x stop-y)]
  
        (if (= fence :stop)
          [true [stop-x stop-y] nil nil]  ;; we are done for :stop, or if no wrapping is needed
          ;; else
          (let [
                ;; Where do we continue from after wrapping
                continue-x
                (if shift-x?
                  (if (pos? stop-x) low-x high-x)
                  stop-x)
                continue-y
                (if shift-x?
                    stop-y
                   (if (pos? stop-y) low-y high-y))
                ;_ (println "  ## continue-x/y" continue-x continue-y)
  
                total-x  (- target-x start-x)
                total-y  (- target-y start-y)
                ;_ (println "  ## total-x/y" total-x total-y)
  
                ;; How much remains of the distance to target
                remaining-x (- total-x consumed-x)
                remaining-y (- total-y consumed-y)
                ;_ (println "  ## remaining-x/y" remaining-x remaining-y)
            
                new-target-x   (+ continue-x remaining-x)
                new-target-y   (+ continue-y remaining-y)]
                ;_ (println "  ## new-target-x/y" new-target-x new-target-y)]
            
            [true [stop-x stop-y] [continue-x continue-y] [new-target-x new-target-y]]))))))


(defn- log-position-maybe
  "If the turtle has a :positions vector, then the current position is appended to it.
  This is used in conjunction with `filled`"
  [turtle position]
  (when-let [positions (:positions @turtle)]
    (swap! turtle assoc :positions (conj positions  position))))


(defn- move-to-impl 
  "Implements move-to. This avoids revealing the optional parameters to the user in documentation."
 ([turtle [x y] & [prev-state res-nodes]]
  ;(println "/move-to-impl" [x y]) 
  (let [prev-state (or prev-state (get-state turtle))
        res-nodes (or res-nodes [])
        [^double start-x ^double start-y :as start-pos] (get-position turtle)
        ;_ (println " .. from" start-pos)
        target-pos [x y]
        
        [^double w ^double h] (get-size true)
        w2 (/ w 2)
        h2 (/ h 2)
        low-pos  [(- w2) (- h2)] ;; bottom-left  
        high-pos [w2 h2]         ;; top-right

        ;; If fence is :stop, then stop at edge
        ;; If fence is :wrap, then divide and recur from opposite edge
        
        ;; 'fence0' can be a keyword or a map.
        ;; if a map, 'fence' can be a keyword or a function which takes turtle and returns a keyword
        ;; if a map 'onfence' can nil or a function which takes a turtle and a keyword
        {:keys [fence onfence] :as fence0} (get-fence)
        fence1 (if fence 
                   (if (fn? fence) 
                       (fence turtle) 
                       fence)
                   fence0)
         
        [fenced? [^double stop-x ^double stop-y] continue-pos new-target-pos]
        (calculate-fencing
            low-pos high-pos
            fence1 
            start-pos target-pos)
          
        c (get-color turtle)
        w (get-width turtle)
        r (is-round turtle)
        node ^Group (:group @turtle)
        parent ^Group (.getParent node)
        line 
        (when (and (is-down turtle) c w)
          (fx/line :x1 start-x :y1  (- start-y)
                   :width w
                   :color (aux/to-color c)
                   :round r))

        res-nodes1 (if line (conj res-nodes line) res-nodes)
      
        ;; prevent deadlock in animation - i.e. if ticker is running, speed will be automatically nil
        speed 
        (when-not (is-ticker-running) (get-speed turtle))

        duration
        (when speed
          (let [
                diff-x (- stop-x start-x)
                diff-y (- stop-y start-y)
                dist (fxu/hypotenuse diff-x diff-y)]
            ;; 500 px per second is "default"
            (* (/ (Math/abs (double dist))
                  (* 500. (double speed)))
             1000.)))

        ;; A duration less than 1.0 may result in no rendering, so we set it to nil.
        duration (if (and duration (< ^double duration 1.)) nil duration)
        
        timeline-synchronizer (promise)]

    (when line
      (fx/now
        (fx/add parent line)
        (.toFront node)))
      
    (if duration
      (do
        (fx/later
          (.play ^Timeline
            (fx/simple-timeline
              duration
              #(deliver timeline-synchronizer :done)
              [(.translateXProperty node) stop-x]
              [(.translateYProperty node) (-  stop-y)]
              (when line [(.endXProperty ^Line line) stop-x])
              (when line [(.endYProperty ^Line line) (- stop-y)]))))
        @timeline-synchronizer)  ;; we wait here til the timeline is done
      (fx/now
        (doto node
          (.setTranslateX stop-x)
          (.setTranslateY (- stop-y)))
        (when line
          (doto ^Line line
            (.setEndX stop-x)
            (.setEndY (- stop-y))))))
      
    (log-position-maybe turtle [stop-x stop-y])
    
    (when (and fenced? onfence) 
          (onfence turtle fence1))
    
    (if continue-pos
        (do
          (set-position turtle continue-pos)
          (recur turtle new-target-pos [prev-state res-nodes1]))
        (append-undo-maybe turtle prev-state res-nodes1)))))


(defn move-to
  "Moves the turtle to the provided position.  
  Does not rotate the turtle towards the position.  If you want it to rotate, do `turn-to` first.
  Does draw line, if pen is down.  
  This is the underlying command for `forward` and `backward`.
  
  Implements [`undo`](var:undo). 

*Example:*
```
(move-to [100 100])
```"
  ([[x y]]
   (move-to (turtle) [x y]))

  ([turtle [x y]]
   (move-to-impl turtle [x y])))


(defn- move [turtle ^double dist]
  (let [heading (get-heading turtle)
        ;_ (println "  ## forward heading:" heading)
        [^double x ^double y] (get-position turtle)
        ;_ (println "  ## forward pos:" pos)
        [^double x-fac ^double y-fac] (fxu/degrees->xy-factor heading)]
    
    (move-to turtle 
             [(+ x (* dist x-fac)) 
              (+ y (* dist y-fac))])))


(defn turtle-polygon []
  (aux/new-polygon 
    [[6 0]  [-6 6]  [-3 0]  [-6 -6]]  
    :color [:white 0.7]
    :fill fx/ANTHRECITE
    :width 0.5))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- transfer-node-values [^Node to-node ^Node from-node]
  (doto to-node
    (.setRotate (.getRotate from-node))
    (.setTranslateX (.getTranslateX from-node))
    (.setTranslateY (.getTranslateY from-node))
    (.setVisible (.isVisible from-node))))


;(ns-unmap *ns* 'clone)
(defmulti clone
          "Clones certain JavaFX Node sub-classes"
          (fn [obj] (class obj)))

;(defmethod clone String [obj]
;  (println "Got a String:" obj)
;  obj)

;(defmethod clone Long [obj]
;  (println "Got a Long:" obj)
;  obj)

(defmethod clone Polygon [^Polygon obj]
  ;(println "Got a Polygon:" obj)
  (doto
    (apply fx/polygon
           (concat
             (vec (.getPoints obj))
             [:fill (.getFill obj) :stroke (.getStroke obj) :strokewidth (.getStrokeWidth obj)]))
    (transfer-node-values obj)))


(defmethod clone Group [^Group obj]
  ;(println "Got a Group!:" obj)
  ;(println "clone:" (.getPoints obj) (.getFill obj) (.getStroke obj) (.getStrokeWidth obj) (userdata
  (apply fx/group (map clone (.getChildren obj))))


(defmethod clone :default [obj]
  (throw (IllegalArgumentException. (format "Don't know how to clone object of type '%s'" (.getName (class obj))))))

;(clone "Hello")
;(clone 1)
;(clone (int 2))
;(println (clone (turtle-polygon)))


(defn get-state 
  "Returns all attributes of the turtle as a map.
  
  **Warning:** As the JavaFX Node is a mutable subclass, the position, heading, and visibility may be inconsistent if the turtle is being manipulated on another thread while this function is executing."
  [turtle] 
  (let [turt @turtle]
    (conj turt
      {:position (get-position turtle)
       :heading  (get-heading turtle)
       :parent   (get-parent turtle)
       :visible  (is-visible turtle)
       :node     (get-node turtle)})))


;(def member-keys #{:position :heading :visible :node :parent})
(def attribute-keys #{:name :speed :color :width :down :round :fill :font :props :undo})


(defn set-state 
  "Applies the passed-in state-map to the turtle. 
  Whichever attributes are present will be applied, including 
  position, heading, visible, etc. Only the undo-buffer is not set.

  Intended used as part of `undo`.

  **Warning!**  
  This will replace any and all attributes in passed-in turtle.
  
  **Warning!**  
  The node will not be cloned. If you are using this function \"off-label\", then make sure to clone the node yourself if it is already on screen/used by another turtle."
  [turtle state]
  ;(println "/set-state")
  ;(user/pprint state)
  (let [turt @turtle
        turt (reduce (fn [t k] 
                       (if-let [v (k state)] (assoc t k v) t)) turt attribute-keys)]
    (when-let [v (:position state)] (set-position_ turt v))
    (when-let [v (:heading state)]  (set-heading_ turt v))
    (when-let [v (:visible state)]  (set-visible_ turt v))
    (when-let [v (:node state)]     (set-node_ turt v))
    (when-let [v (:parent state)]   (set-parent_ turt v))))


;; TODO: update this doc.
(defn new-turtle
  "Creates and returns a new turtle.
  You can choose to \"hold on to it\" for later access. 
   
  The last turtle to be created is always the default turtle, and any \"turtle commands\" will be applied to it unless you wish otherwise.
  
  See topic [Turtles](:Turtles) for more information.

*Examples:*
```
(new-turtle)                 ;; easy
(new-turtle :name \"Tina\")  ;; this can help you identify you turtle(s) - in printout or when asked directly
```

  ***

  **Advanced**
  
  A turtle is an standard key-value structure. It contains a JavaFX Group, which represents it graphically.
  And it holds a number of other internal parameters - including one substructure 'props' 
  
  'props' holds any keyed values the user wishes to set on the turtle - even allowing the user to use the turtle as a pure storage.
  
  The group wraps a \"node\" - any JavaFX Node.
  This and any other attribute of the turtle can be overridden on creation - and most manipulated any time later also.
    "
  ;; TODO: Turtle should take its defaults in a short-and-sweet command
  [& {:keys [position heading visible node parent name speed color width round down fill font undo props]
      :or {position (get-default :position)
           heading (get-default :heading)
           visible (get-default :visible)
           node (clone (get-default :node))
           parent (get-default :parent)
           name (get-default :name)
           speed (get-default :speed)
           color (get-default :color)
           width (get-default :width)
           round (get-default :round)
           fill (get-default :fill)
           down (get-default :down)
           font (get-default :font)
           undo (get-default :undo)
           props (get-default :props)}
      :as args}]
  ;(user/pprint ["  ## args:" args])

  ;; validate color, fill, font
  (aux/to-color color)
  (aux/to-color fill)
  (aux/to-font font)

  (let [turtle
        (atom
          (map->Turtle
            {:name name
             :speed speed
             :color color
             :width width
             :round round
             :down down
             :fill fill
             :font font
             :undo undo
             :undo-buffer (get-default :undo-buffer)
             :props props
             :group (fx/group node)}))]

       (register-turtle turtle)
       (fx/now
         (doto @turtle
           (set-position_ position)
           (set-heading_ heading)
           (set-visible_ visible))
         (when parent (set-parent_ @turtle parent)))
    
    turtle))
 

(defn clone-turtle
  "Allows you to clone a turtle, and override chosen attributes in one easy command.
  
  All parameters are taken from the passed-in turtle, any overrides are applied, an then `new-turtle` is called.
  See [`new-turtle`](var:new-turtle) for more.
"
  [turtle & {:as args}] 
  (let [state 
        (get-state turtle) 
        new-state 
        (conj state args)
        new-state1 
        (if (.getParent ^Node (:node new-state))
            (update new-state :node clone)  ;; Important to replace the node with a clone.
            new-state)]
    (apply new-turtle (reduce concat (seq new-state1)))))


(defn get-all-turtles
  "Returns a global list of all turtles.  

  The user may filter the list based on state and props, and manipulate the individual turtles directly.  

  The same turtle won't appear twice in the list.  
  The list is ordered based on creation order."
  []
  (-> @(get-screen) :turtles vals))


(defn- register-turtle
  "Appends turtle to the global registry if it is not already registered."
  [turtle]
  ;(println "register-turtle turtle-count (approx):" (count @turtles_))
  (swap! (get-screen) assoc-in [:turtles turtle] turtle)
  turtle)


(defn- unregister-turtle
  "Remove the turtle from the global list."
  [turtle]
  (swap! (get-screen) update-in [:turtles] dissoc turtle)
  nil)


(defn delete-turtle 
  "Removes the given turtle from the screen, and from the global list of all turtles.
  See also [`get-all-turtles`](var:get-all-turtles).
  
  **Warning:** Deleting a turtle while it is still being moved has unknown consequences.
  "
  [turtle]
  (unregister-turtle turtle)
  (un-parent_ @turtle)
  nil)


(defn delete-all-turtles 
  "Removes all turtles from the screen, and empties the global list of turtles.
  The optional argument indicates whether or not to leave a single turtle on the screen. 'true' is default.
  
  **Warning:** Deleting a turtle while it is still being moved has unknown consequences.
  "
 ([]
  (delete-all-turtles true))

 ([keep-1-turtle?]
  (let [
        all-turtles 
        (get-all-turtles)
        
        one-turtle 
        (when keep-1-turtle? 
              (turtle))  ;; call once in stead of for every filter application
        
        turtles 
        (if keep-1-turtle? 
            (filter #(not= % one-turtle) 
                    all-turtles)
            all-turtles)]
    
    (doseq [turtle turtles]
      (delete-turtle turtle)))
  nil))


(def ^:dynamic *turtle* nil)


(defn turtle
  "Returns the turtle object.     
  Creates and shows a screen with default size if one is not already created.
  
  *Example:*
```
(turtle)
```

  ***

  **Advanced**
  
  First looks in dynamic scope for *turtle* and returns it,
  else looks in the global list of turtles and returns the last one,
  else creates a new turtle (which gets added to the global list).

  If you want to control another turtle than the latest one,
  then use the command [`with-turtle`](var:with-turtle) "
  []
  (screen)  ;; Ensure visible screen
  (if-let [turt *turtle*]
    turt
    (if-let [turt (last (get-all-turtles))] 
      turt
      (new-turtle :name "Tom"))))


(defmacro with-turtle
  "Applies any turtle-commands in the 'body' to the specified turtle.

  Uses dynamic binding for turtle which means that the specified must be bound lexically if to be used inside a thread inside the body.

  See topic [Turtles](:Turtles) for mor information.

  **WARNING!** `with-turtle` cannot be used within the body of `filled`. (Not sure why...)\n

  *Example:*
```
(let [lars (new-turtle :name \"Lars\")
      john (new-turtle :name \"John\")]
  (with-turtle lars
    (forward 50) (left 90))) ;; Lars moves.
  (forward 70) (right 90)))  ;; John moves, 
                             ;;   because he is the most recently created 
                             ;;   and therefore the default turtle.
```"
  [turtle & body]
  `(do
     (binding [*turtle* ~turtle]
       ~@body)))

;(pprint (macroexpand-1 '(with-turtle (make-turtle) (forward 100))))
;(with-turtle (make-turtle) (forward 100))


(defn- de-iconify-maybe 
  "If stage is new, it won't be iconified.
    If the stage is open, then the user is aware of it, and it doesn't need any focus either.
    Only if the stage is iconified, should it be brought to the user's awareness."
  [^Stage stage]
  (when (.isIconified stage)
    (fx/later
      (doto stage
        (.setIconified false)
        (.toFront)))))


(defn to-front
  "Has the turtle window move to the front - e.g. ensures that the turtle window gets \"focus\".
   Useful so it can receive onkey-events immediately."
 ([]
  (to-front (get-screen)))
 ([screen]
  (when (is-screen-visible screen)
    (fx/later (.toFront ^Stage (:stage @screen))))))


(defn- relayout-stage [stage]
  (when stage
    (let [
          [^double w ^double h] (fx/WH stage)]
      (doto stage
       (.setWidth (inc w))
       (.setHeight (inc h))
       (.setWidth w)
       (.setHeight h)))))


  ;; Should this behave differently - not clear away (all) turtles?
(defn clear
  "Removes all graphics from screen.
  The optional argument indicates whether or not to leave a single turtle on the screen. 'true' is default.
  
  **Warning:** This also clears away all but the last turtle (or all the turtles)!

  *Example:*
```
(clear)       ;; leaves the last turtle in place
(clear false) ;; clears away all the turtles
```"
 ([]
  (clear true))
 ([keep-1-turtle?]
  (let [{:keys [stage root axis]} @(get-screen)
        
        nodes-to-keep-pred  
        (if keep-1-turtle?
            ;; a set *is* a predicate function
            #{(:group @(turtle)) axis} 
            #{axis})]
    
    (delete-all-turtles keep-1-turtle?)

    (fx/later
      (fx/children-set-all 
        root 
        (filter nodes-to-keep-pred (fx/children root)))
      (relayout-stage stage)))

  nil))


(defn set-background
  "Sets the screen background.
  
  See [Color](:Color) for more information."
  [color]
  (let [c (if (#{:background :default} color)
            (get-screen-default :background)
            color)]
    (-> @(get-screen) :pane (fx/set-background (aux/to-color c)))
    nil))


(defn get-background
  "Returns the screen's background color in the form it was set."
  []
  (-> @(get-screen) :background))


(defn get-size
  "Returns the screen's size.
  If 'actual?' is `true` then whatever the screen has been resized to.
  Otherwise what it was set to programmatically. "
  [& [actual?]]
  (if actual?
    (when-let [scene (:scene @(get-screen))]
      (-> scene fx/WH))
    (-> @(get-screen) :size)))


(def allowed-fence-values #{:stop :wrap :none})


(defn set-fence
  "Sets the screen fence. The fence controls behavior for turtles at the edge of the screen. 
  
'type' can be one of the following keywords:
- `:stop` - The turtle doesn't move any further.
- `:wrap` - The turtle continues its movement at the opposite edge of the screen.
- `:none` - No fence.  The turtle just keeps going unhindered.
  
  `:none` is default.
  
  *Alternatively, 'type' can be a map.*

  The map must contain the key `:fence` with a value which is either one of the above keywords, 
  or a 1-arg function which receives a turtle, and returns one of the above keywords.
  This function should have no side-effects as it may be called repeatedly.
  
  The map may also contain a second key `:onfence` which must contain a function which receives a turtle and the keyword for the fence that was applied, if any fencing was applied.
  This function will only be called when fencing is applied, and is good for side-effects.
    
  This is useful e.g. when making a game (such as Asteroids) and you want some turtles (shots) to stop, and you will delete them, while other turtles (rocks) should wrap.
  
  
*Examples:*
```
(set-fence :wrap)

(set-fence :none)

(set-fence {:fence :wrap})

(set-fence {:fence  (fn [turtle] (println turtle) :stop)})

(set-fence {:fence   (fn [turtle] (println turtle) :stop)
            :onfence (fn [turtle fence] (when (= fence :stop) (delete-turtle turtle)))})
```"
  
  [fence]
  (assert (or (map? fence) (#{:stop :wrap :none :default :fence} fence))
          "'type' must be a function or one of the specified keywords.")
  ;; TODO: assert the return value when calling the function!
  ;; TODO: also maybe ':slide' (continue sideways along the fence) , ':bounce' (like a ball), ':reverse' (do a 180 and continue).
  (swap! (get-screen) assoc :fence fence)
  nil)


(defn get-fence
  "Returns the screen's fence type."
  []
  (-> @(get-screen) :fence))


(defn set-axis-visible 
  "Shows/hides x/y axis at origo.

*Example:*
```
(set-axis-visible true)
```
"
  [bool]
  (-> @(get-screen) :axis (#(.setVisible ^Group % bool))))


(defn is-axis-visible 
  "Returns `true` if axis is visible, else `false`."
  []
  (-> @(get-screen) :axis (#(.isVisible ^Group %))))


(defn set-border-visible 
  "Shows/hides a border axis at edge of screen.

*Example:*
```
(set-border-visible true)
```
"  
  [bool]
  (-> @(get-screen) :border (#(.setVisible ^Rectangle % bool))))


(defn is-border-visible
  "Returns `true` if border is visible, else `false`."
  []
  (-> @(get-screen) :border (#(.isVisible ^Rectangle %))))


(defn swap-prop
  "'function' is a 1-arg function which takes the old value, and returns whatever new value should be set.

  See [Properties](:Properties) for more information."
  ([key function]
   (swap-prop (turtle) key function))
  ([turtle key function]
   (swap! turtle update-in [:props key] function)
   nil))

(defn set-prop
  "Sets a property on an turtle.
  
  See [Properties](:Properties) for more information."
  ([key value]
   (set-prop (turtle) key value))
  ([turtle key value]
   (swap! turtle assoc-in [:props key] value)
   nil))

(defn get-props
  "Returns a map of all props set on a turtle.

  See [Properties](:Properties) for more information."
  ([]
   (get-props (turtle)))
  ([turtle]
   (:props @turtle)))


(defn get-prop
  "Returns the prop value of the turtle for the given 'key'.

  See [Properties](:Properties) for more information."
  ([key]
   (get-prop (turtle) key))
  ([turtle key]
   (-> turtle get-props key)))


(defn- set-node_
  "Applied to deref-ed turtle."
  [turt node]
  (set-now (:group turt) node))


(defn set-node
  "Sets the \"node\" for the turtle. 'node' can be any JavaFX Node."
  ([node]
   (set-node (turtle) node))
  ([turtle node]
   (set-node_ @turtle node)))


(defn get-node
  ([]
   (get-node (turtle)))
  ([turtle]
   (-> @turtle :group (#(.getChildren ^Group %)) first)))


(defn- get-parent_ 
  "Applied to deref-ed turtle."
  [turt]
  (-> turt :group (#(.getParent ^Group %))))


(defn- un-parent_
  "Applied to deref-ed turtle.  
  Removes the turtle's group from the parent."
  [turt]
  (when-let [p (get-parent_ turt)]
    (fx/now
      (fx/remove p (:group turt)))))


(defn- set-parent_
  "Applied to deref-ed turtle. Set's the parent if it is not already set to that parent."
  [turt parent]
  (if-let [p (get-parent_ turt)]
    (when (not= p parent)
      (un-parent_ turt)
      (add-now parent (:group turt)))
    (add-now parent (:group turt)))
  nil)


(defn get-parent
  "Advanced.  More info to come."
  ([]
   (get-parent (turtle)))
  ([turtle]
   ;(-> @turtle :group (#(.getParent ^Group %)))))
   (get-parent_ @turtle)))


(defn get-defaults
  "The complete turtle defaults.
  May be used for re-setting a turtle or when creating a new turtle.
  Implemented as a function because calling it touches screen, 
  and so it has the side-effect of creating as screen if one hasn't already been created."
  []
  {:position [0 0]
   :heading 0
   :visible true
   :parent (get-root)
   :node (turtle-polygon)

   :name "<John Doe>"
   :speed 1
   :color :black
   :width 1
   :down true
   :round false
   :fill :dodgerblue
   :font ["Source Code Pro" :normal 14]
   :props {}
   :undo 0
   
   :undo-buffer []})


(defn get-default
  "Looks up a single value from defaults-map."
  [k]
  ((get-defaults) k))


(defn get-screen-defaults []
  {:background :white
   :fence :none
   :size [600 450]
   :axis-visible false  
   :border-visible false})


(defn get-screen-default [k]
  ((get-screen-defaults) k))


(defn- turn [turtle ^double degrees]
  (let [new-angle (+ ^double (get-heading turtle) degrees)]
    (set-heading turtle new-angle)
    nil))


(defn- arc 
  [turtle ^double radius ^double degrees]
  (let [prev-state 
        (get-state turtle)
        undo-nodes_ (atom [])
        
        orig-heading (get-heading turtle)
        orig-pos (get-position turtle)]

    (if (< radius 1.5) ;; if the radius is to small, then simply turn on the spot
      (turn turtle degrees)
      (let [step-len (min 1.5 (max (/ radius 5.) 15.))  ;; a 5th of radius is pretty good, min 1, max 5
            circumference (* 2 radius Math/PI)  ;; if 360 degrees
            travel (* circumference (/ (Math/abs degrees) 360.)) ;; how much of the circ. we will travel
            step-cnt (/ travel step-len) ;; break travel into tiny (hardly visible steps).
            step-turn (/ degrees step-cnt) ;; turn per step
            round? (is-round turtle)
            speed (get-speed)]
        (set-round turtle true)
        (when speed (set-speed turtle (* 5. ^double speed))) ;; lets move faster through the turn
        (binding [*undo-nodes* undo-nodes_]
          (turn turtle (/ step-turn 2.))  ;; get the tilt right by starting with a half-turn
          (dotimes [_ step-cnt]
            (move turtle step-len)
            (turn turtle step-turn))
          (turn turtle (- (/ step-turn 2.))) ;; then subtract a final half-turn
          ;; return 'round' and 'speed' to their original
          (set-round turtle round?)
          (when speed (set-speed turtle speed))
      
          ;; now make final accurate adjustment - to offset and drifts in heading or position.
          ;; heading - only needed if we did an arc
          (set-heading turtle (+ ^double orig-heading degrees)))
  
          ;; position - maybe needed also if we "cheated"
      
          ;; https://math.stackexchange.com/questions/332743/calculating-the-coordinates-of-a-point-on-a-circles-circumference-from-the-radiu#432155
          ; xP2 = xP1 + r sin θ
          ; yP2 = yP1 − r (1− cos θ)
          ;; TODO: Do some math! Help!!
          ;; (move-to turtle [? ?])

        ;; Do this outside the binding, so that it will actually be appended.
        (append-undo-maybe turtle prev-state @undo-nodes_)))

    nil))


(defn arc-left
  "Draws and arc (a bow) curving clockwise from current position.
  
  'radius' is the distance to an imagined center of a circle - to the left of the turtle. The bigger the radius, the softer the curve. 'radius' should be a positive number or zero.
  
  'degrees' is how far around the theoretical circle the turtle will go:  
  `360` will make a complete circle. `90` will make a quarter circle, etc.
  'degrees' may be negative, causing the arc to go the other way, as if `arc-right`.

  The arc is speeded up such that in most cases it is instantaneous. If you want to see it animate, then you will need to set the turtle's speed to something like `0.5` or less before doing an arc.
  
  Implements [`undo`](var:undo).
    
*Examples:*
```
(arc-left 50 360)  ;; A full circle with diameter 100.

(rep 4 
  (forward 60) 
  (arc-left 20 90))  ;; A square with rounded corners - sized 100x100.

```
  "
 ([radius degrees]
  (arc-left (turtle) radius degrees))
 ([turtle radius degrees]
  (arc turtle radius degrees)))


(defn arc-right
  "Same as `arc-left`, but counter-clockwise.

See [`arc-left`](var:arc-left) for more information."
  ([radius degrees]
   (arc-right (turtle) radius degrees))
  ([turtle radius  degrees]
   (arc turtle radius (- ^double degrees))))


(defn left
  "Rotates the turtle counter-clockwise.  
  A negative number will result in a clockwise rotation.

  Implements [`undo`](var:undo).

  *Example:*
```
(left 90)
(left -90)  ;; Rotates to the right.
```"
 ([degrees]
  (left (turtle) degrees))
 ([turtle degrees]
  (turn turtle degrees)
  nil))


(defn right
  "Similar to `left`, but in the opposite direction."
  ([degrees]
   (right (turtle) degrees))
  ([turtle ^double degrees]
   (turn turtle (- degrees))
   nil))


(defn forward
  "Moves the turtle forward `distance` in the direction the turtle is heading.  
  A negative number is also possible. It will result in the turtle moving backward.  

  Implements [`undo`](var:undo).
  
  *Example:* 
```
(forward 50)
(forward -50)
```"
 ([distance]
  (forward (turtle) distance))
 ([turtle distance]
  (assert (number? distance) (format "'distance' must be a number. Got %s" distance))
  (move turtle distance)
  nil))


(defn backward
  "Moves the turtle backward `distance` in the direction the turtle is heading.  
  A negative number is also possible. It will result in the turtle moving forward.  

  *Example:* 
```
(backward 50)
(backward -50)  ;; same as (forward 50)
```"
  ([distance]
   (backward (turtle) distance))
  ([turtle distance]
   (assert (number? distance) (format "'distance' must be a number. Got %s" distance))
   (move turtle (-  (double distance)))
   nil))


(defn- set-heading_
  "Applied to deref-ed turtle."
  [turt angle]
  (fx/now (.setRotate ^Group (:group turt) (- ^double angle))))


(defn set-heading
  "Rotates the turtle to the given heading:  
  `0` is facing right.  
  `90` is facing up.  
  `180` is facing left.  
   etc.  

  Implements [`undo`](var:undo).
  
  *Example:* 
```
(set-heading 90)
```
See [Cartesian coordinate system](https://en.wikipedia.org/wiki/Cartesian_coordinate_system) for more information.
"
 ([heading] 
  (set-heading (turtle) heading))
 ([turtle heading]
  (let [prev-state (get-state turtle)
        angle   
        (if (#{:heading :default} heading)
            (get-default :heading)
            heading)]
    (set-heading_ @turtle angle)
    (append-undo-maybe turtle prev-state))
  nil))


(defn get-heading
  "
  Returns the turtles current absolute heading (angle).  
  The returned number will be always be a positive number ranging from 0 to 359.  
  See [`set-heading`](var:set-heading) for more details.
  
  *Example:* 
```
(heading)
```  
  See [Cartesian coordinate system](https://en.wikipedia.org/wiki/Cartesian_coordinate_system) for more information."
 ([]
  (get-heading (turtle))) 
 ([turtle]
  (- ^double 
     (rem (-> @turtle :group ^double (#(.getRotate ^Group %))) 
          360.0))))


(depr/defn heading
           {:deprecated {:in "2018.1"}
                        :use-instead get-heading
                        :print-warning :always}
  []
  (get-heading))


(defn- set-position_ 
  "Applied to deref-ed turtle."
  [turt pos]
  (fx/now (fx/set-translate-XY (:group turt) (flip-Y pos))))


(defn set-position
  "Moves the turtle to the given absolute position (coordinates) relative to \"origo\" (center of screen.)  
  'x' is right, 'y' is up.

  The move is instant, and no lines are drawn.

  'position' is a 2-item vector [x y].  
  If 'x' or 'y' are \"falsy\" (i.e. `nil` or `false`), then that part is ignored.

  Implements [`undo`](var:undo).
  
  *Examples:*
```
  (set-position [30 40])
  (set-position [30 nil]) ;; only x is changed, not y
```
  See [Cartesian coordinate system](https://en.wikipedia.org/wiki/Cartesian_coordinate_system) for more information."
 ([[x y]]
  (set-position (turtle) [x y]))
 ([turtle [^double x ^double y :as position]]
  (let [prev-state (get-state turtle)
        [x ^double y] 
        (if (#{:position :default} position)
            (get-default :position)
            position)]
       (set-position_ @turtle [x y])
       (append-undo-maybe turtle prev-state)
    nil)))


(defn get-position
  "Returns the turtle's current absolute position.

  The value is a 2-item vector: [x y]  
  The vector can be \"destructed\", or 'first' or 'second' can be called on it to get just the x or y value.

  *Examples:*
```
(position)  ;; returns the position
(let [[x y] (position)] ...) ;; destructing
(let [x (first (position))] ...)
```
See [`set-position`](var:set-position) for more details.
"
 ([]
  (get-position (turtle)))
 ([turtle]
  (let [node ^Group (:group @turtle)]
    [^double (.getTranslateX node) (- ^double (.getTranslateY node))])))


(depr/defn position
           {:deprecated {:in "2018.1"}
            :use-instead get-position
            :print-warning :always}
           []
           (get-position))


(defn set-down 
  "Sets the pen in down or up position.  If 'bool' is `true`, then the pen will be set to down.  
  The purpose of this is to allow easier toggling in code.  
  
  Any value that is \"truth-y\" in Clojure is allowed.
  
  *examples:*
```
(set-down true)      ;; down
(set-down false)     ;; up
(set-down nil)       ;; up
(set-down (is-down)) ;; the opposite of what it was
```"
 ([bool]
  (set-down (turtle) bool))
 ([turtle bool]
  (let [down? 
        (if (#{:down :default} bool)
            (get-default :down)
            bool)]
    (swap! turtle assoc :down (boolean down?)))
  nil))


(depr/defn set-pen-down
           {:deprecated {:in "2018.2"
                         :use-instead set-down
                         :print-warning :always}}
           [down?]
           (set-down down?))



(defn is-down
  "Returns `true` if the turtle's pen is \"down\", else `false`.

  *Example:* 
```
(is-down)
```"
 ([]
  (is-down (turtle)))
 ([turtle]
  (:down @turtle)))


(defn set-round
  "Sets the pen's shape to a round or not round (square) shape.  
  If 'bool' is `true`, then the pen will be set to round.  
  
  Any value that is \"truth-y\" in Clojure is allowed.
  
  *examples:*
```
(set-round true)  ;; round
(set-round false) ;; square
(set-round nil)   ;; square

```"
  ([bool]
   (set-round (turtle) bool))
  ([turtle bool]
   (if (#{:round :default} bool)
       (swap! turtle assoc :round (get-default :round))
       (swap! turtle assoc :round (boolean bool)))
   nil))


(defn is-round
  "Returns `true` if the turtle's pen is round, else `false`.
  Default is `false` - i.e. the turtle always starts with a square pen.

  *Example:* 
```
(is-round)
```"
  ([]
   (is-round (turtle)))
  ([turtle]
   (:round @turtle)))


(depr/defn is-pen-down
           {:deprecated {:in "2018.2"
                         :use-instead is-down
                         :print-warning :always}}
           []
           (is-down))



(defn pen-up
  "Picks up the pen, so when the turtle moves, no line will be drawn.
 
  *Example:* 
```
(pen-up)
```"
 ([]
  (pen-up (turtle)))
 ([turtle]
  (set-down turtle false)
  nil))


(defn pen-down
  "Sets down the pen, so when the turtle moves, a line will be drawn.

  *Example:* 
```
(pen-down)
```"
 ([]
  (pen-down (turtle)))
 ([turtle]
  (set-down turtle true)
  nil))


(depr/defn get-pen-down
           {:deprecated {:in "2018.1"}
            :use-instead is-down
            :print-warning :always}
           []
           (is-down))


(defn set-speed
  "Sets the speed of the turtle to a number or `nil`.  
  The number is multiples of \"standard speed\".

  `1` or `1.0` is default/standard speed  
  `2` or `2.0` is double speed  
  `0.5` or `1/2` is half speed  
  etc.  

  `nil` is no speed, i.e. no animations -> as fast as possible.  
  A speed over `100` has no increase effect.  

  A speed set such that the animation duration is less than 1, results in speed `nil` being used.
  
  *Example:* 
```
(set-speed 1)
(set-speed 2)
(set-speed 0.5)
(set-speed 1/2)
(set-speed nil)
```"
 ([number]
  (set-speed (turtle) number))

 ([turtle number]
  (if (#{:speed :default} number)
    (swap! turtle assoc :speed (get-default :speed))
    (do
      (assert (nil-or-not-neg-number? number)
              (format "set-speed requires a positive number or 'nil'. Got '%s'" number))
      (swap! turtle assoc :speed number)))
  nil))


(defn get-speed
  "Returns a number or `nil` - whatever the speed is.  

  *Example:*
```
(get-speed)
```

  See [`set-speed`](var:set-speed) for more information.\n
" 
 ([]
  (get-speed (turtle)))
 ([turtle]
  (:speed @turtle)))


(depr/defn speed
           {:deprecated {:in "2018.1"}
            :use-instead get-speed
            :print-warning :always}
           []
           (get-speed))


(defn set-color
  "Sets the turtle's (pen) color.
  It is used when drawing lines, or as border for filled areas, or when writing.
  
  *Examples:*
```
(set-color \"orange\")
(set-color \"#0000ee\") ;; a blue
(set-color Color/CORNFLOWERBLUE)
(set-color (Color/color 0 255 0)) ;; a green
```  
  See topic [Color](:Color) for more information."
 ([color]
  (set-color (turtle) color))
 ([turtle color]
  (if (#{:color :default} color)
    (swap! turtle assoc :color (get-default :color))
    (do
      (aux/to-color color)  ;; Easiest way to assert color early. Probably not necessary to "memoize."
      (swap! turtle assoc :color color)))
  nil))


(depr/defn set-pen-color
           {:deprecated {:in "2018.0"
                         :use-instead set-color
                         :print-warning :always}}
           [color]
           (set-color color))


(defn get-color
  "Returns the turtle's (pen) color  in the form it was set.
  
  *Example:*
```
(get-color)
```

  See topic [Color](:Color) for more information.
"
 ([]
  (get-color (turtle)))
 ([turtle]
  (:color @turtle)))


(depr/defn get-pen-color
           {:deprecated {:in "2018.0"
                         :use-instead get-color
                         :print-warning :always}}
           []
           (get-color))


(defn set-width 
  "Sets the width of the turtle's pen - e.g. how wide/thick the drawn line will be.  There are no upper limits.  
  'width' must must be a positive number or `nil`.   
  `1` is default.
  
  *Examples:*
```
(set-width 1)
(set-width 1.0)
(set-width 2/3)
(set-width 20)
(set-width nil)  ;; no line will be drawn.
```"
 ([width]
  (set-width (turtle) width))
 ([turtle width]
  (if (#{:width :default} width)
    (swap! turtle assoc :width (get-default :width))
    (do
      (assert (nil-or-not-neg-number? width)
          "'width' must be a positive number or nil")
      (swap! turtle assoc :width width)))
  nil))


(defn get-width 
  "Returns the width of the pen.
  
*Example:*
```
(get-width)
```"
 ([]
  (get-width (turtle)))
 ([turtle]
  (:width @turtle)))


(defn- set-visible_
  "Applied to deref-ed turtle."
  [turt bool]
  (fx/now (.setVisible ^Group (:group turt) bool)))


(defn set-visible
  "Makes the turtle visible or not.
  Any value that is \"truth-y\" in Clojure is allowed.
   
   *examples:*
```
(set-visible true)  ;; show
(set-visible false) ;; hide
(set-visible nil)   ;; hide
```"
 ([bool]
  (set-visible (turtle) bool))
 ([turtle bool]
  (set-visible_  @turtle (if (#{:visible :default} bool) (get-default :visible) (boolean bool)))
  nil))


(defn is-visible
  "Returns `true` if the turtle is visible, else `false`.

  *Example:* 
```
(is-visible)
```"
 ([]
  (is-visible (turtle)))
 ([turtle]
  (-> @turtle :group (#(.isVisible ^Group %)))))


(depr/defn is-showing
           {:deprecated {:in "2018.1"}
            :use-instead is-visible
            :print-warning :always}
           []
           (is-visible))


(defn show
  "Makes the turtle visible.

  *Example:* 
```
(show)
```"
 ([]
  (show (turtle)))
 ([turtle]
  (set-visible turtle true)
  nil))


(defn hide
  "Makes the turtle *not* visible.

  *Example:*
```
(hide)
```"
 ([]
  (hide (turtle)))
 ([turtle]
  (set-visible turtle false)
  nil))


(defn set-fill
  "Sets the turtle's fill.
  It will be used for filling figures.
  
  It is similar to `set-color`.
  "
 ([color]
  (set-fill (turtle) color))
 ([turtle color]
  (if (#{:fill :default} color) 
    (swap! turtle assoc :fill (get-default :fill))
    (do  
      (aux/to-color color)  ;; assert color
      (swap! turtle assoc :fill color)))
  nil))


(defn get-fill
  "Returns the turtle's fill color in the form it was set"
 ([]
  (get-fill (turtle)))
 ([turtle]
  (:fill @turtle)))


(defn set-font
  "Sets the font. 
  'font' may not be `nil`
  
There are a number of optional ways to set font:
- `family`
- `size`
- `[family]`
- `[size]`
- `[family size]`
- `[family weight size]`
- `[family weight posture size]`
- an instance of JavaFX Font.
  
  'family' is a string naming the font-face or font-type.
  'size' is any number
  'weight' is one of `:normal` `:bold`  
  'posture' is one of `:regular` `:italic`  

  If a font is not available on your machine, then a system font will automatically be selected. 

*Examples:*
```
(set-font \"Arial\")
(set-font 12)
(set-font [\"Arial\" 12])
(set-font [\"Arial\" :bold 12])
(set-font [\"Arial\" :normal :italic 12])
```"
  ([font]
   (set-font (turtle) font))
  ([turtle font]
   (if (#{:font :default} font)
     (swap! turtle assoc :font (get-default :font))
     (do
       (aux/to-font font)
       (swap! turtle assoc :font font)))
   nil))

(aux/to-font (fx/new-font 14))


(defn get-font
  "Returns the font set on the turtle in the form that it was set."
  ([]
   (get-font (turtle)))
  ([turtle]
   (:font @turtle)))


(defn write
  "Tells the turtle to write 'text' on the screen.
  'text' can be anything, including numbers, structures, or objects.
  
  The turtle uses the 'font' and 'color 'you have set on it.
  See [`set-font`](var:set-font) and [`set-color`](var:set-color) for more about font and fill. 
  
  The text is written such that the top left corner of the text starts where the turtle is positioned, 
  and is printed at whatever heading the turtle has; including upside-down if the turtle is facing left.
  
  If 'move?' is set to `true`, then the turtle will move to the end of the text.
  Alternatively, it will stay at the beginning of the text.
  
  If 'text' is multi-line (contains one or more `\\n`), then the turtle will write it as multiple lines.
  
  Implements [`undo`](var:undo).
  
  *Examples:*
```
(write \"Hello World!\")
(write \"How\\nare\\nyour?\") ;; Written over 3 lines.
(write 42)                    ;; Writes the number.
(write \"I am done!\" true)   ;; Writes, and then moves past the text.
```"
  
 ([text]
  (write text false))
 ([text move?]
  (write (turtle) text move?))
 ([turtle text move?]
  (let [{:keys [parent ^double heading position font color down :as prev-state]} (get-state turtle)  
        txt ^Text
        (fx/text (str text) 
                 :font (aux/to-font font))]
    (doto txt
      (.setTextOrigin VPos/TOP)
      (.setBoundsType TextBoundsType/VISUAL)
      (-> .getTransforms (.add (Rotate. (- heading) 0 0)))
      (fx/set-translate-XY (flip-Y position)))
    (when color (.setFill txt (aux/to-color color)))
    (add-now parent txt)
    (when move?
      (let [down? down]
        (doto turtle 
          (set-down false) 
          (forward (-> txt .getBoundsInLocal .getWidth))
          (set-down down?))))
    (append-undo-maybe turtle prev-state [txt]))))
;; TEST
"
(reset)\n(left 30)\n(forward 30)\n(write \"HelloW\" true)\n(set-font 30)\n(set-fill [255 0 0 0.5])\n(println (get-color))\n(write \"World!\\n ... not\" true)\n(forward 30)\n(set-color :green)\n(set-font [\"Geneva\" 36])\n(write \"Done!\")\n
"
;; TODO: Text position + dimensions are slightly off.  Can it be fixed?


(defmacro filled-with-turtle 
  "Given a turtle, will catch all movements of the turtle within the body, and then fill that area with whatever the turtle's 'fill' is at the end.
  
  If 'fill' is `nil`, then no fill is made.
  See [`set-fill`](var:set-fill) for more information on 'fill' and 'color'.
   
  Use this if you want to be explicit about which turtle to use, otherwise simply use `filled`.
  See [`with-turtle`](var:with-turtle) and topic [Turtles](:Turtles) for more on multiple turtles.
  
  Implements [`undo`](var:undo).
    Important! Filling is a discrete (separate) step after the movements are done, and so calling `undo` once after a fill will only remove the fill, not reverse any of the movements.\n  Also, if doing an undo during the movements, in a fill, will not remove the point in the \"log\", and the point will still be part of the fill shape.
    
  *Warning 1:* 
  `filled` and `filled-with-turtle` can be used within the body of `with-turtle`, but not the other way around.
   The reason is that the \"inner turtle\" will capture the tracking. 

  *Warning 2* 
  1. You will need to move the turtle at least 2 steps, so the fill can form a polygon of at least points.
  "
  [t & body]
  ;; Make a not of the current layer of the turtle.
  `(let [layer# (-> (get-parent ~t) .getChildren (.indexOf (-> ~t deref :group)))]
     ;; This will cause move to "log" positions using `log-position-maybe`
     (swap! ~t assoc :positions [(get-position ~t)])
     ;; Execute the body using the passed-in turtle
     (with-turtle ~t
       ~@body)
     ;; Collect the logged positions
     (let [positions# (map flip-Y (-> ~t deref :positions))
           moves# (count positions#)]
       (if (< moves# 3)
         (binding [*out* *err*] 
           (printf "WARNING! The turtle used for 'filled' needs to move at least twice. Got %s\n" (dec moves#)))
         ;; Get the turtles fill.  (Setting fill to nil is a way of preventing fill)
         (when-let [fill# (get-fill ~t)]
           ;; Build a polygon
           (let [p# (apply fx/polygon
                           (flatten [positions# :fill (aux/to-color fill#) :stroke nil]))]
             (append-undo-maybe ~t (get-state ~t) [p#])
             ;; We insert the polygon at the layer the turtle was at start.
             (fx/now (fx/add-at (get-parent ~t) layer# p#)))))
       ;; Stop further logging of positions
       (swap! ~t dissoc :positions)
       nil)))


(defmacro filled 
  "A short form of `filled-with-turtle`.  
  The current turtle is used, as opposed to an explicit turtle.

  Use `filled` if you are already running in a `with-turtle`, or just want to use the current turtle. 
  Use `filled-with-turtle` if you want to specify a turtle.
  
  Implements [`undo`](var:undo).
  
  See [`filled-with-turtle`](var:filled-with-turtle)  for more information and *warnings*.
  "
  [& body]
  `(let [t# (turtle)]
     (filled-with-turtle t# ~@body)))


(defn set-name
  "Sets the name of the turtle. The name can be any value or type you want. No type-checking is done.  
  
  *Example:* 
```
(set-name \"John\")
(set-speed :Kate)
(set-speed nil)  ;; You really hate him.  ;-)
```"
  ([name]
   (set-name (turtle) name))

  ([turtle name]
   (if (#{:name :default} name)
     (swap! turtle assoc :name (get-default :name))
     (swap! turtle assoc :name name))
   nil))


(defn get-name
  "Returns whatever the turtles name is set to.  
  
  *Example:*
```
(get-name)
```

  See [`set-name`](var:set-name) and [`new-turtle`](var:new-turtle) for more information.
"
  ([]
   (get-name (turtle)))
  ([turtle]
   (:name @turtle)))


;;;;;;;;;;;;;;;



(defn home
  "Moves the turtle back to the center of the screen.
  Sets heading to `0` (and position to `[0 0]`).
  No line is drawn even if the pen is down.
  (If you want a line then do `(move-to [0 0])`

  *Example:*
```
(home)
```"
 ([]
  (home (turtle)))
 ([turtle]
  (let [pen-down? (is-down turtle)]
    (pen-up turtle)
    (set-heading turtle 0)
    (set-position turtle [0 0])
    (when pen-down? (pen-down turtle)))
  nil))


(defn reset
  "A combined screen and turtle command.  
  Clears the screen, and center the current turtle, leaving only one turtle.
  The optional argument indicates whether or not to leave a single turtle on the screen. 'true' is default. 

  **Warning:** `(clear)` also clears away all but the last turtle (or all if optional argument is false-y).
  
  *Example:* 
```
(reset)        ;; leaves one turtle on the screen.
(reset false)  ;; leaves the screen blank
```
"
 ([]
  (reset true))
 ([keep-1-turtle?]
  (screen)
  (stop-ticker)  ;; important to do before clearing any nodes - as the ticker may continue to effect something.
  (clear keep-1-turtle?)
  (when keep-1-turtle?
        (show) (set-speed :default) (pen-up) (home) (pen-down)
        (set-color :default) (set-fill :default) (set-font :default) (set-width :default) (set-round :default) (set-undo 0)) 
  
  (set-background :default)  (reset-onkey)

  nil))


(defn sleep
  "Causes the thread to \"sleep\" for a number of milliseconds.
  1 second = 1000 milliseconds

  *Example:* 
```
(sleep 2000)  ;; Sleeps for 2 seconds
```"
  [milliseconds]
  (Thread/sleep milliseconds)
  nil)

;(defmacro rep [n & body]
;    `(dotimes [~'_ ~n]
;       ~@body))


(defmacro rep
  "Macro.  
  Repeatedly executes body (presumably for side-effects) from 0 through n-1 times.  
  
  *Example:*
```
(rep 3
     (forward 50) 
     (left 120))
 ```
If you need to get the count in the body, then use the standard Clojure `dotimes` in stead:
```
(dotimes [i 3]
         (println i)
         (forward 50)
         (left 120))
```
See topic [Clojure](:Clojure) for more information."

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


(defn- new-ticker
  ([f]
   (new-ticker (/ 1000. 30) f))
  ([millis f]
   (doto 
     (fx/timeline nil (fx/new-keyframe millis f))
     (.setCycleCount Animation/INDEFINITE))))


(defn ^Timeline get-ticker
  "returns the current ticker, if one has been set"
  []
  (:ticker @(get-screen)))


(defn set-ticker
  "A single ticker set on the screen. It takes a no-args function and an optional time (in milliseconds).
  This is useful in games and in animations.
  'function' is the no-args function that will be called at every tick.
  'interval-in-milliseconds' is the time between each tick. Default is 30 ticks per second.

  The ticker is not automatically started or stopped, but is stopped if the Turtle window is closed, or if a new function is set on st-ticker.  
  
  **Warning!** Preferable set the turtles 'speed' to nil, to avoid running lots of animations at every tick, unless perhaps the ticks are very slow.
  Combining ticks and animations in turtles is unpredictable!
"  
  ([function]
   (set-ticker (/ 1000. 30) function))
  ([interval-in-milliseconds function]
   (when-let [t (get-ticker)]
     (println "/set-ticker - stopping ticker")
     (.stop t))  ;; to avoid memory leak!
   (let [screen_ (get-screen)
         t (new-ticker interval-in-milliseconds #(binding [*screen* screen_] (function)))]
     (swap! screen_ assoc  :ticker t)
     nil)))


(defn start-ticker 
  "Starts the ticker. 
  See [`set-ticker`](var:set-ticker) for more."
  []
  (when-let [t (get-ticker)]
    (fx/later (.play t))))


(defn stop-ticker
  "Stops the ticker. 
  See [`set-ticker`](var:set-ticker) for more."
  [& verbose?]
  (when (is-ticker-running)
    (let [t (get-ticker)]
      (fx/later (.stop t))
      (when verbose?
        (println "ticker stopped")))))


(defn is-ticker-running
  "Returns true/false if a ticker has been set, else nil"
  []
  (when-let [t (get-ticker)]
    (= (.getStatus t) Animation$Status/RUNNING)))

;(let [t (new-ticker #(println "ticker1"))]
;  (.play t)
;  (sleep 500)
;  (.stop t))

;(set-ticker #(println "tick"))
;(start-ticker)
;(sleep 500)
;(stop-ticker)
;(sleep 500)
;(start-ticker)
;(sleep 500)
;(stop-ticker)


(defn get-all-onkey_
  []
  (:onkey @(get-screen)))


(defn get-all-onkey
  "Returns a map of all onkey function pair set on the screen."
  []
  @(get-all-onkey_))


(defn reset-onkey 
  "Removes all onkey handlers from the screen."
  []
  (reset! (get-all-onkey_) {}))


(defn- set-onkey-handlers 
  "Is called on screen becoming visible to set onkey-handlers on stage."  
  [^Scene scene]
  (doto  scene
    (.setOnKeyTyped (fx/char-typed-handler (get-all-onkey_)))
    (.setOnKeyPressed (fx/key-pressed-handler (get-all-onkey_)))))


(defn- assert-onkey-key [k]
  (assert (or (and (vector? k) (> (count k) 0) (every? keyword? k))
              (and (string? k) (= (count k) 1)))
          (format "Key for assoc/dissoc/get-onkey must be one of vector-of-keywords or 1-char string. Got  %s" k)))


(defn- uppercase-and-makeset-keywords [v]
  (if (vector? v)
    (set 
      (map #(keyword (cs/upper-case (name %))) 
           v))
    v))


(defn set-onkey
  "Adds/replaces an onkey function.

  'key-combo-or-char-str' is either a vector of one or more keywords form of a KeyCode - representing a pressed combination - or a string with a single character, matching a keystroke (or combination) as returned by the operating system.
    
  'function' is a no-arg function that will be called every time the specified character is typed or key-kombo pressed.
  
  See [JavaFX KeyCode](https://docs.oracle.com/javase/8/javafx/api/index.html?javafx/scene/input/KeyCode.html) for an overview of all available keykodes.
 
*Examples:*
```
(set-onkey [:UP] #(println \"Got UP\")
(set-onkey [:UP :SHORTCUT] #(println \"Got CTRL-UP or CMD-UP\")
(set-onkey \"a\" #(println \"Got a lower-case a\")
(set-onkey \"Å\" #(println \"Got an upper-case Å\")
``  
"
  [key-combo-or-char-str function]
  (assert-onkey-key key-combo-or-char-str)
  (assert (fn? function)
          (format "'function' passed to set-onkey must be a function. Got  %s" function))
  (let [screen_ (get-screen)]
    (swap! (get-all-onkey_) 
           assoc 
           (uppercase-and-makeset-keywords key-combo-or-char-str) #(binding [*screen* screen_] (function)))
    nil))


(defn unset-onkey
  "Removes the specified function from the onkey-map.
  See [`set-onkey`](var:set-onkey) for more."
  [key-combo-or-char-str]
  (assert-onkey-key key-combo-or-char-str)
  (swap! (get-all-onkey_) dissoc (uppercase-and-makeset-keywords key-combo-or-char-str))
  nil)


(defn get-onkey
  "Returns the specified function from the onkey-map.
  See [`set-onkey`](var:set-onkey) for more."
  [key-combo-or-char-str]
  (assert-onkey-key key-combo-or-char-str)
  ((get-all-onkey) (uppercase-and-makeset-keywords key-combo-or-char-str)))

;(set-onkey [:up] #(println "UP!"))
;(user/pprint (get-all-onkey))
;((get-onkey [:up]))


(defn new-screen
  ;; TODO: Implement support for mounting screen in alternative layout.
  "Returns a new screen object.
  The screen is not visible. (It is not mounted in a window (stage).
  You will need to make it visible (or not) with a separate call to `set-screen-visible`.  
  If simply want a visible default screen, use `screen`.
  
  If no 'size'  parameter is passed, the screen will use a default size.
  The size is simply stored, and applied whenever the the screen is made visible.
*examples:*
```
(get-screen)
(get-screen [400 300])  ;; [w h]
```
"
 ([]
  (new-screen (get-screen-default :size)))
 ([[w h :as size]]
  (let [
        fence
        (get-screen-default :fence)
        
        background
        (get-screen-default :background)

        ;; TODO: Set the border directly on the pane in stead?
        border
        (doto
          (fx/rectangle :arc 4 :fill Color/TRANSPARENT)
          (fx/set-stroke Color/CORNFLOWERBLUE 2)
          (fx/set-translate-XY [2 2])
          (.setVisible false))

        axis
        (doto ^Group
              (fx/group
                (fx/line :x1 -100 :x2 100 :color Color/CORNFLOWERBLUE :width 2)
                (fx/line :y1 -100 :y2 100 :color Color/CORNFLOWERBLUE :width 2))
          (.setVisible false))

        root
        (fx/group axis)

        pane
        (doto
          (fx/pane border root)
          (fx/set-background (aux/to-color background)))]

    (atom
      (map->Screen
        {:stage nil
         :scene nil
         :pane pane  ;; This is the part that is root on scene.
         :root root   ;; This is the root of all turtle artifacts. It is scentered on the scene.
         :axis axis
         :border border
         :size size
         :background background
         :fence fence
         :turtles (ordered-map)
         :ticker nil
         ;; onkey is an atom as key-handlers can read from an atom directly
         :onkey (atom {})})))))



(defonce ^:dynamic *screen* (new-screen))


(defmacro with-screen
  "Used for overriding the default screen.
  Intended used for tooling.
  
  Equal to doing:
  ```
  (binding [*screen* screen]
   ;; body
   )
  ```"
  [screen & body]
  `(do
     (binding [*screen* ~screen]
       ~@body)))


(defn get-screen 
  "Simply returns which ever screen is bound to *screen* 
  - either via `with-screen` or directly in code."
  []
  *screen*)


(defn- resize-screen [screen]
  (let [{:keys [size scene stage]} @screen
        ;; figure out the diffs for the chrome
        diff-w (- (.getWidth stage) (.getWidth scene))
        diff-h (- (.getHeight stage) (.getHeight scene))
        [^double w ^double h] size]
    (fx/set-WH stage [(+ w diff-w) (+ h diff-h)])
    nil))


(defn is-screen-visible
  "Returns `true` or `false` for whether the screen is visible ornot."
 ([]
  (is-screen-visible (get-screen)))

 ([screen]
  (boolean (and (:scene @screen) (:stage @screen)))))


(defn- make-hidden [screen]
  (let [{:keys [stage scene border root]} @screen]

    (binding [*screen* screen]
      (stop-ticker true))
    ;(output/interrupt-all-sessions true))

    (when scene
      ;; a dummy pane, so as to release the screen's pane from the scene for later re-attaching to new scene
      (.setRoot scene (fx/pane)))
    
    (when (.isShowing stage)
      (fx/now (.close stage)))

    (doto ^Rectangle border
      (-> .widthProperty .unbind)
      (-> .heightProperty .unbind))

    (doto ^Group root
      (-> .layoutXProperty .unbind)
      (-> .layoutYProperty .unbind))

    (swap! screen dissoc :stage :scene)    
    nil))


(defn- make-visible [screen]
  (let [{:keys [pane size border root]} @screen
        scene
        (doto
          (fx/scene pane :size size)
          (set-onkey-handlers))

        stage
        (fx/now
          (fx/stage
            :scene scene
            :title "Turtle Screen"
            :resizable true
            :sizetoscene true
            :tofront true
            :alwaysontop true
            :onhidden #(set-screen-visible screen false)))]

       (doto ^Rectangle border
         (-> .widthProperty (.bind (-> scene .widthProperty (.subtract 4))))
         (-> .heightProperty (.bind (-> scene .heightProperty (.subtract 4)))))
       
       (doto ^Group root
         (-> .layoutXProperty (.bind (-> scene .widthProperty (.divide 2))))
         (-> .layoutYProperty (.bind (-> scene .heightProperty (.divide 2)))))

       (swap! screen assoc :scene scene :stage stage)
       nil))


(defn set-screen-visible
  "Sets the screen's visibility."
  ([visible?]
   (set-screen-visible (get-screen) visible?))
  ([screen visible?]
   (cond 
     (and visible? (not (is-screen-visible screen)))  
     (make-visible screen)
     
     (and (not visible?) (is-screen-visible screen))        
     (make-hidden screen))  
   nil))


(defn set-screen-size 
  "Sets the screen size 
  - whether screen is visible or not."
 ([[w h :as size]]
  (set-screen-size (get-screen) size))  
 ([screen [w h :as size]]
  (swap! screen assoc :size (or size (get-screen-default :size)))
  (when (is-screen-visible screen)
        (resize-screen screen))))


(defn get-screen-size
  "Returns the current screens size as a vector [w h] 
  - whether screen is visible or not."
 ([]
  (get-screen-size (get-screen)))
 ([screen]
  (:size @screen)))


(defn screen 
  "Makes the screen visible, and possibly resizes it;
   either the default screen or one bound to *screen* using `with-screen` (advanced) or explicitly.
  There is always a default screen.  
  
  The screen is made visible and (re-)sized to 'size'.  
  
  If the screen is already visible, but \"minimized\", it will be \"un-minimized\" and brought to the front.

*Examples:*  
```
(screen)
(screen [400 300])
```  
  "
 ([]
  (screen nil))
 ([[w h :as size]]
  (let [screen (get-screen)]
    (when size (set-screen-size screen size))
    (set-screen-visible screen true)
    (de-iconify-maybe (:stage @screen))
    nil)))
