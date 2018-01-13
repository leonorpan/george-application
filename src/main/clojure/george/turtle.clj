;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle

    "George Turtle Geometry implements only the basic (procedural) single-turtle  functions of the original UCB Logo TG, not (for now) the object-oriented multi-turtle environment of Python.

Furthermore, certain changes have been made to some of the function-names to make them more consistent, readable, an 'clojure-y' - mainly inserting hyphens in multi-word function names.

We use 'standard' mode for TG as this is most in line with underlying standard math:
1. Turtle home is origo.
2. Origo is center of screen.
3. X increases to the right, Y increases upwards.
4. Degrees are counterclockwise. 0 degrees is facing right.

    "

  (:require
    [defprecated.core :as depr]
    [flatland.ordered.map :refer [ordered-map]]
    [george.javafx :as fx]
    [george.javafx.util :as fxu]
    [george.turtle.gui :as gui]
    [george.util.singleton :as singleton]
    [george.application.ui.styled :as styled])
  (:import
    [javafx.scene.paint Color Paint]
    [javafx.scene Group Node]
    [javafx.scene.shape Line Rectangle Polygon]
    [javafx.scene.text Font FontWeight]
    [javafx.stage Stage]))

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
  get-color
  get-width
  set-visible
  is-visible
  is-down
  is-round
  get-speed
  set-heading
  get-heading
  set-position
  get-position
  get-node
  get-parent
  default-font
  reset
  to-color
  to-font
  screen
  turtle
  register-turtle)


;; Empty records allow for easy typing of maps.
(defrecord Turtle [])
(defrecord Screen [])


(defn- nil-or-not-neg-number? [x]
  (or (nil? x)
      (and (number? x)
           (not (neg? (double x))))))


(defn- add-now [parent node]
  (fx/now (fx/add parent node)))

(defn- add-later [parent node]
  (fx/later (fx/add parent node)))


(defn- set-now [parent node]
  (fx/now (fx/set parent node)))


(defn- get-root
  "Returns the top-level parent for all turtle rendering."
  [screen]
  (:root @screen))


(defn- add-node [screen node]
  (add-now (get-root screen) node))


(defn- forward-impl [turtle ^double dist]
  ;(println "  ## do-forward*" dist)
  (let [ang (get-heading turtle)
        [^double x ^double y] (get-position turtle)
        [^double x-fac ^double y-fac] (fxu/degrees->xy-factor ang)
        target-x (+ x (* dist x-fac))
        target-y (-  (+ y (* dist y-fac)))
        c (get-color turtle)
        w (get-width turtle)
        r (is-round turtle)
        node (:group @turtle)
        line
        (when (and (is-down turtle) c w)
              (fx/line :x1 x :y1  (- y) 
                       :width w
                       :color (to-color c)
                       :round r))

        duration
        (when-let [speed (get-speed turtle)]
          ;; 500 px per second is "default"
          (* (/ (Math/abs (double dist))
                (* 500. (double speed)))
             1000.))

        ;; A duration less than 1.0 may result in no rendering, so we set it to nil.
        duration (if (and duration (< ^double duration 1.)) nil duration)]

    (when line
        (add-node (screen) line)
        (fx/later (.toFront ^Group node)))

    (if duration
      (fx/synced-keyframe
        duration
        [(.translateXProperty ^Group node) target-x]
        [(.translateYProperty ^Group node) target-y]
        (when line [(.endXProperty ^Line line) target-x])
        (when line [(.endYProperty ^Line line) target-y]))
      (do
        (doto ^Group node
          (.setTranslateX target-x)
          (.setTranslateY target-y))
        (when line
          (doto ^Line line
            (.setEndX target-x)
            (.setEndY target-y)))))))


(defn turtle-polygon []
  (fx/polygon 6 0  -6 6  -3 0  -6 -6  
              :fill fx/ANTHRECITE 
              :stroke (to-color [:white 0.7]) 
              :strokewidth 0.5))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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


(defn- default-state []
  {:parent nil ; (get-root (screen))
   :name "<anonymous>"
   :node (turtle-polygon)
   :position [0 0]
   :heading 0
   :visible true
   :speed 1
   :color :black
   :width 1
   :round false
   :fill :gray
   :down true
   :font (default-font)
   :history-size 0
   :props {}})


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
  
  The group wraps a \"shape\", which is any JavaFX node.
  This and any other attribute of the turtle can be overridden on creation - and most manipulated any time later also.
  
  A `clone-turtle` function is coming soon.
  "
  ;; TODO: implements 'clone-turtle' ...

  [& {:keys [name parent node position heading visible speed color width round down fill font history-size props]
      :or {parent (get-root (screen))
           name "<anonymous>"
           node (turtle-polygon)
           position [0 0]
           heading 0
           visible true
           speed 1
           color :black
           width 1
           round false
           fill :gray
           down true
           font (default-font)
           history-size 0
           props {}}
      :as args}]
  ;(user/pprint ["  ## args:" args])

  ;; validate color, fill, font
  (to-color color)
  (to-color fill)
  (to-font font)

  (let [group 
        (fx/group node)         

        turtle
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
             :history-size history-size
             :history []
             :props props
             :group group}))]
    
       (doto turtle
         (set-position position)
         (set-heading heading)
         (set-visible visible))

    (register-turtle turtle)
    (when parent (add-later parent group))
    
    turtle))


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


(defn clone-turtle
  "Allows you to clone a turtle, and override chosen attributes in one easy command.
  
  All parameters are taken from the passed-in turtle, any overrides are applied, an then `new-turtle` is called.
  See [`new-turtle`](var:new-turtle) for more.
"
  [turtle & {:as args}] 
  (let [state 
        (get-state turtle) 
        new-state 
        (assoc (conj state args)
               ;; Important to replace the node with a clone.
               :node (clone (:node state)))]
        
    (apply new-turtle (reduce concat (seq new-state)))))


;;;;;;;


(def ^:private turtles_ (atom (ordered-map)))


(defn get-all-turtles
  "Returns a global list of all turtles.  

  The user may filter the list based on state and props, and manipulate the individual turtles directly.  

  The same turtle won't appear twice in the list.  
  The list is ordered based on creation order."
  []
  (vals @turtles_))


(defn- register-turtle
  "Appends turtle to the global registry if it is not already registered."
  [turtle]
  ;(println "register-turtle turtle-count (approx):" (count @turtles_))
  (swap! turtles_ assoc turtle turtle)
  turtle)


(defn- unregister-turtle
  "Remove the turtle from the global list."
  [turtle]
  (swap! turtles_ dissoc turtle)
  nil)


(defn delete-turtle 
  "Removes the given turtle from the screen, and from the global list of all turtles.
  See also [`get-all-turtles`](var:get-all-turtles).
  
  **Warning:** Deleting a turtle while it is still being moved has unknown consequences.
  "
  [turtle]
  (unregister-turtle turtle)
  (when-let [p (get-parent turtle)]
    (fx/later
      (fx/remove p (:group @turtle)))))


(defn delete-all-turtles 
  "Removes all turtles from the screen, and empties the global list of turtles.

  **Warning:** Deleting a turtle while it is still being moved has unknown consequences.
  "
  []
  (let [turtles (get-all-turtles)]
    (reset! turtles_ (ordered-map))
    (doseq [t turtles]
      (when-let [p (get-parent t)]
        (fx/later
          (fx/remove p (:group @t)))))))
        

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
  (if-let [turt *turtle*]
    turt
    (if-let [turt (last (get-all-turtles))]
      turt
      (new-turtle :name "Tom"))))


(defmacro with-turtle
  "Applies any turtle-commands in the 'body' to the specified turtle.

  Uses dynamic binding for turtle which means that the specified must be bound lexically if to be used inside a thread inside the body.

  See topic [Turtles](:Turtles) for mor information.

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


;;;;;;;;;;;;;


(def ^:private DEFAULT_SCREEN_SIZE [600 450])
(def ^:private SCREEN ::screen)
(def ^:private STAGE ::stage)


(defn- get-screen []
  (singleton/get SCREEN))


(defn- create-screen 
  "Returns an outer node which centers 'root' group.
  The root group is the actual parent for all items. 
  The outer node, and is the root of a (stage) scene, or framed in a layout"
  [size]
  (let [
        background 
        :white
        
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

        container 
        (doto
          (fx/pane root border)
          (fx/set-background (to-color background)))]    
    (atom
      {:container container  ;; This is the part that becomes root on scene.
       :root root   ;; This is the root of all turtle artifacts. It is scentered on the scene.
       :axis axis
       :border border
       :size size
       :background background})))    


(defn set-background 
  "Sets screen background.
  
  See [Color](:Color) for more information."
  [color]
  (let [c (to-color color)]
    (-> (screen) deref :container (fx/set-background c))
    (swap! (screen) assoc :background color)))


(defn get-background
  "Returns the screens background color."
  
  []
  (-> (screen) deref :background))


(defn- get-or-create-screen
  ([]
   (get-or-create-screen DEFAULT_SCREEN_SIZE))
  ([size]
   (singleton/get-or-create SCREEN #(create-screen size))))


(defn- delete-screen
  "This should be called when closing the turtle screen. It, in first calls delete-all-turtles."
  []
  (when (get-screen)
    (delete-all-turtles)
    (singleton/remove SCREEN)))


(defn- new-screen-scene [scrn]
  (let [
        scene
        (fx/scene (:container @scrn) :size (:size @scrn) :fill fx/RED)]

    (doto ^Rectangle (:border @scrn)
      (-> .widthProperty (.bind (-> scene .widthProperty (.subtract 4))))
      (-> .heightProperty (.bind (-> scene .heightProperty (.subtract 4)))))

    (doto ^Group (:root @scrn)
      (-> .layoutXProperty (.bind (-> scene .widthProperty (.divide 2))))
      (-> .layoutYProperty (.bind (-> scene .heightProperty (.divide 2)))))

    scene))


(defn- get-stage []
  (when-let [stg ^Stage (singleton/get STAGE)]
    (when (.isIconified stg)
      (fx/later
        (doto stg
          (.setIconified false)
          (.toFront))))
    stg))


(defn- create-stage [scrn]
  (fx/now
    (let [scene
          (new-screen-scene scrn)
          stg
          (fx/stage
            :scene scene
            :title "Turtle Screen"
            :resizable true
            :sizetoscene true
            :tofront true
            :alwaysontop true
            :onhidden 
            #(do
               (delete-screen)
               (singleton/remove SCREEN)
               (singleton/remove STAGE)))]
      (doto stg
        (gui/set-cm-menu-on-stage)
        (styled/add-icon)))))

(defn- ^Stage get-or-create-stage [scrn]
  (if-let [stg (get-stage)]
    stg
    (singleton/put STAGE (create-stage scrn))))


(defn- get-new-size [scrn size]
  (if (nil? size)
    (if scrn (:size @scrn) DEFAULT_SCREEN_SIZE)
    size))


(defn screen
  "Returns a screen object, creating and showing one, and creating and adding a turtle on it.
  It (re-)sized to size.  If no size, then sizes to default, which is [600 450].
  
  Screen will not automatically create a turtle.  If you want a screen with a turtle on it, then call `turtle` or any other turtle command in stead.
  "
 ([]
  (screen nil))

 ([[w h :as size]]
  (let [scrn (get-screen)
        stg (get-stage)] 
    (if (and (nil? size) stg scrn)
        scrn ;; Don't resize.  
        (let [size (get-new-size scrn size)
              scrn (get-or-create-screen size)
              stg  (get-or-create-stage scrn)
              scene (.getScene stg)
      
              ;; figure out the diffs for the chrome
              diff-w (- (.getWidth stg) (.getWidth scene))
              diff-h (- (.getHeight stg) (.getHeight scene))
              [^double w ^double h] size]
          (fx/set-WH stg [(+ w diff-w) (+ h diff-h)])
          (doto scrn
            (swap! assoc :size size)))))))


(defn set-axis-visible 
  "Shows/hides x/y axis at origo.

*Example:*
```
(set-axis-visible true)
```
"
  [bool]
  (-> (screen) deref :axis (#(.setVisible ^Group % bool))))


(defn is-axis-visible 
  "Returns `true` if axis is visible, else `false`."
  []
  (-> (screen) deref :axis (#(.isVisible ^Group %))))


(defn set-border-visible 
  "Shows/hides a border axis at edge of screen.

*Example:*
```
(set-border-visible true)
```
"  
  [bool]
  (-> (screen) deref :border (#(.setVisible ^Rectangle % bool))))


(defn is-border-visible
  "Returns `true` if border is visible, else `false`."
  []
  (-> (screen) deref :border (#(.isVisible ^Rectangle %))))


;;;;;;;;;;;;;;;;;;;;


(defn swap-prop
  "'function' is a 1-arg function which takes the old value, and returns whatever new value should be set."
  ([key function]
   (swap-prop (turtle) key function))
  ([turtle key function]
   (swap! turtle update-in [:props key] function)))


(defn set-prop
  ([key value]
   (set-prop (turtle) key value))
  ([turtle key value]
   (swap! turtle assoc-in [:props key] value)))


(defn get-props
  "Returns a map of all props set on turtle."
  ([]
   (get-props (turtle)))
  ([turtle]
   (:props @turtle)))


(defn get-prop
  "Returns the prop value of the turtle for the given 'key'."
  ([key]
   (get-prop (turtle) key))
  ([turtle key]
   (-> turtle get-props key)))


(defn set-node
  "Sets the \"shape\" for the turtle. 'shape' can be any JavaFX Node."
  ([shape]
   (set-node (turtle) shape))
  ([turtle shape]
   (set-now (:group @turtle) shape)))


(defn get-node
  ([]
   (get-node (turtle)))
  ([turtle]
   (-> @turtle :group (#(.getChildren ^Group %)) first)))


(defn get-parent
  ([]
   (get-parent (turtle)))
  ([turtle]
   (-> @turtle :group (#(.getParent ^Group %)))))


;; TODO: Consider these font-functions vs fx/new-font (which uses some keywords ...)

(defn default-font
  ([]
   (default-font "Regular" 14))
  ([size]
   (default-font "Regular" size))
  ([weight size]
   (fx/new-font "Source Code Pro" weight size)))


(defn to-font
  ([font-or-family-or-size]
   (assert (not (nil? font-or-family-or-size))
           "'font' can not be set to 'nil'.")
   (cond
     (instance? Font font-or-family-or-size)  
     font-or-family-or-size

     (or (number? font-or-family-or-size) (string? font-or-family-or-size)) 
     (fx/new-font font-or-family-or-size)))

  ([family size]
   (fx/new-font family size))
  ([family weight size]
   (Font/font
     ^String family
     (if (string? weight) (FontWeight/findByName weight) (FontWeight/findByWeight weight))
     (double size))))


;;;;;;;;;;;;;;;;;;;;;;;;;;


;; TODO: use clojure.spec for assertions.
(defn to-color 
  "Returns an instance of javafx.scene.paint.Paint, usually Color.
  'color' may be one of:  

- instance of sublcass of javafx.scene.paint.Paint, usually Color - i.e. `Color/GREEN`  
- str, a web-color - named or hex  - i.e. `\"green\"` or `\"#00ff00\"`  
- keyword, a named web-color - i.e. `:green`  
- a single number for gray, integer 0-255 or 0.0-1.0, ranging from black to white
- `[gray opacity]`, 'gray' as above, and alpha 0.0-1.0
- `[red green blue]`, either all all doubles 0.0-1.0 or all integers - i.e. [0.0 1.0 0.0] or [0 255 0]
- `[red green blue opacity]`, as above, but with 'opacity'; a double 0.0-1.0 where 0 is transparent and 1 is opaque - i.e.  [0 255 0 1.0]
- `nil`, returns `nil`.

  This function is  normally *not* used by the developer, but always called by `set-color`, and agian by `forward` when pen is down.
  
  However, you can use this to \"pre-validate\" a color if you want, such that you know that it will be acceptable, and so you can see the web-color it will result in.
  "
  [color]
  (cond
    (nil? color) nil

    (instance? Paint color) color
    (string? color) (Color/web color)
    (keyword? color) (Color/web (name color))
    (number? color) (if (float? color) (Color/gray color) (Color/grayRgb color))

    ;; web-color or gray + opacity
    (and (vector? color) (= 2 (count color)))
    (let [[g o] color]
      (when o 
        (assert (<= 0.0 o 1.0) (format "[_ opacity] must be a number between 0.0 and 1.0. Got '%s'" o)))
      (cond
        (string? g)  (Color/web g o)
        (keyword? g) (Color/web (name g) o)
        (float? g)   (Color/gray g o)
        :default (Color/grayRgb g o)))
    
    ;; RGB + opacity
    (vector? color)
    (let [cnt (count color)
          _ (assert (<= 3 cnt 4)
                    "'color' must be '[red green blue]' or '[red green blue opacity]'")
          rgb (subvec color 0 3)
          ints? (every? integer? rgb)
          floats? (every? float? rgb)
          _ (assert (or ints? floats?)
                    (format "'[red green blue _]'  must be all integers or all floats or doubles. Got '%s'" rgb))

          [r g b o] color]

      (when o 
        (assert (<= 0.0 o 1.0) (format "'[_ _ _ opacity]' must be a float or double. Got '%s'" o)))

      (if o
        (if ints? (Color/rgb r g b o) (Color/color r g b o))
        (if ints? (Color/rgb r g b) (Color/color r g b))))

    ;; ehhh ...
    :default
    (throw (IllegalArgumentException. (str "Type of color is not acceptable. Got " color)))))
;(println (to-color :red))
;(println (to-color "red"))
;(println (to-color "#ff0000"))
;(println (to-color [:red 1.0]))
;(println (to-color ["red" 1.0]))
;(println (to-color Color/RED))
;(println (to-color ["#ff0000" 1.0]))
;(println (to-color 0.5))  ;; gray
;(println (to-color 127))  ;; gray
;(println (to-color [0.5 1.0]))  ;; gray
;(println (to-color [127 1.0]))  ;; gray
;(println (to-color [1.0 0.0 0.0]))  ;; red
;(println (to-color [1.0 0.0 0.0 1.0])) ;; red
;(println (to-color [255 0 0]))  ;; red
;(println (to-color [255 0 0 1.0])) ;; red
;(println (to-color [255 0 0 1])) ;; red
;;; FAIL
;(println (to-color [255 0.1 0 1.0])) ;; Fail: Mix of mumber types
;(println (to-color [255 1 0 127])) ;; Fail: Opacity is not [0.0 .. 1.0]



(defn- hello [turtle]
  (let [n (:name @turtle)]
    (println (format "Hello.  My name is %s.  %s the Turtle." n n))
    nil))


(defn tom
  "Same as `(turtle)`, but Tom introduces himself."
  []
  (reset)
  (delete-all-turtles)
  (doto (new-turtle :name "Tom")
        (hello)))


(defn- rotate [turtle ^double degrees]
  (let [new-angle (+ ^double (get-heading turtle) degrees)]
    (set-heading turtle new-angle)
    nil))


(defn left
  "Rotates the turtle counter-clockwise.  
  A negative number will result in a clockwise rotation.

  *Example:*
```
(left 90)
(left -90)  ;; Rotates to the right.
```"
 ([degrees]
  (left (turtle) degrees))
 ([turtle degrees]
  (rotate turtle degrees)
  nil))


(defn right
  "Similar to `left`, but in the opposite direction."
  ([degrees]
   (right (turtle) degrees))
  ([turtle ^double degrees]
   (rotate turtle (- degrees))
   nil))


(defn forward
  "Moves the turtle forward `distance` in the direction the turtle is heading.  
  A negative number is also possible. It will result in the turtle moving backward.  

  *Example:* 
```
(forward 50)
(forward -50)
```"
 ([distance]
  (forward (turtle) distance))
 ([turtle distance]
  (assert (number? distance) (format "'distance' must be a number. Got %s" distance))
  (forward-impl turtle distance)
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
   (forward-impl turtle (-  (double distance)))
   nil))


(defn set-heading
  "Rotates the turtle to the given heading:  
  `0` is facing right.  
  `90` is facing up.  
  `180` is facing left.  
   etc.  

  *Example:* 
```
(set-heading 90)
```
See [Cartesian coordinate sytem](https://en.wikipedia.org/wiki/Cartesian_coordinate_system) for more information.
"
 ([angle] 
  (set-heading (turtle) angle))
 ([turtle ^double angle]
  (.setRotate ^Group (:group @turtle) (- angle))
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


(defn set-position
  "Moves the turtle to the given absolute position (coordinates) relative to \"origo\" (center of screen.)  
  'x' is right, 'y' is up.

  'position' is a 2-item vector [x y].  
  If 'x' or 'y' are \"falsy\" (i.e. `nil` or `false`), then that part is ignored.

  *Examples:*
```
  (set-position [30 40])
  (set-position [30 nil]) ;; only x is changed, not y
```
  See [Cartesian coordinate system](https://en.wikipedia.org/wiki/Cartesian_coordinate_system) for more information."
 ([[x y]]
  (set-position (turtle) [x y]))
 ([turtle [^double x ^double y]]
  (let [node ^Group (:group @turtle)]
    (fx/synced-keyframe
      250
      (when x [(.translateXProperty node) x])
      (when y [(.translateYProperty node) (- y)])))
  nil))


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
  (set-down (turtle) (boolean bool)))
 ([turtle bool]
  (swap! turtle assoc :down (boolean bool))
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
   (set-round (turtle) (boolean bool)))
  ([turtle bool]
   (swap! turtle assoc :round (boolean bool))
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
  (assert (nil-or-not-neg-number? number)
          (format "set-speed requires a positive number or 'nil'. Got '%s'" number))
  (swap! turtle assoc :speed number)
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
  (to-color color)  ;; Easiest way to assert color early. Probably not necessary to "memoize."
  (swap! turtle assoc :color color)
  nil))


(depr/defn set-pen-color
           {:deprecated {:in "2018.0"
                         :use-instead set-color
                         :print-warning :always}}
           [color]
           (set-color color))


(defn get-color
  "Returns the turtle's (pen) color.
  
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
  (assert (nil-or-not-neg-number? width)
          "'width' must be a positive number or nil")
  (swap! turtle assoc :width width)
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
  (.setVisible ^Group (:group @turtle) (boolean bool))
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
   (swap! turtle assoc :name name)
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


;; TODO: Should this behave differently - not clear away turtles?
(defn clear
  "Removes all graphics from screen.

  **Warning:** This also clears away all but the last turtle!

  *Example:*
```
(clear)
```"
  []
  (let [scrn (screen)
        stg ^Stage (singleton/get STAGE)
        ;; A hack to force the scene to re-render/refresh everything.
        [^double w ^double h] (fx/WH stg)
        _ (.setWidth stg (inc w))
        _ (.setHeight stg (inc h))
        _ (.setWidth stg  w)
        _ (.setHeight stg h)

        root (get-root scrn)
        trtl (turtle)
        filtered (filter #(or (= % (:group @trtl)) 
                              (= % (:axis @scrn))) 
                         (fx/children root))]

      (fx/later
        (fx/children-set-all root filtered)))
  nil)


(defn home
  "Moves the turtle back to the center of the screen.
   Sets heading to `0` (and position to `[0 0]`).

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

  Same as calling:   
```
(clear) (show) (set-speed 1) (pen-up) (home) (pen-down)
(set-color :black) (set-width 1) (set-background :white) (set-round false) 
```
  **Warning:** `(clear)` also clears away all but the last turtle!
  
  *Example:* 
```
(reset)
```"
  []
  (clear) (show) (set-speed 1) (pen-up) (home) (pen-down)
  (set-color :black) (set-width 1) (set-background :white) (set-round false)

  nil)


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


(defn run-sample
  "A simple test program which uses many of the available turtle commands."
  []
  (reset)
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
  (set-color "red")
  (println "pen color:" (get-color))
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

  (hide)
  nil)


;;;;;;;;; Documentation details


(def topic-welcome "# Welcome

Click on any command or topic in the list to to the left for more information.

*Here is a super quick piece of code you can try:*
```
(reset)
(set-color \"orange\")
(rep 5
  (forward 50)
  (left 144))
```
*(Type it yourself, or copy-and-paste it into an Input or Editor and do 'Run'.)*

**Enjoy!**")


(def topic-color
  "# Color

George uses JavaFX for its graphics.  This gives you a lot of power to do whatever you want with colors.  
There are both easy and more advanced things you can do.


## Easy

The easiest is to use named HTML color such as `\"red\"`, `\"orange\"`, `\"blue\"`.  
You can find a good list online: [HTML Color Values](https://www.w3schools.com/colors/colors_hex.asp).

Or, if you prefer, you can use the same colors defined in 'Color', such as `Color/CORNFLOWERBLUE`.  
You can find the list online: [Color - Fields](https://docs.oracle.com/javase/8/javafx/api/javafx/scene/paint/Color.html#field.summary) .


## Medium

You can mix your own color. To do so, use HTML colors, and specify Your mix of Red Green Blue with hexadecimal number.  
A hex number is a number that goes from `0` to `f`.  So to make red, you can write`\"#f00\"` or `\"#ff0000\"`.  
You can experiment with mixing HTML colors online: [Colors RGB](https://www.w3schools.com/colors/colors_rgb.asp) .


## Special

You can also control and mix colors any way you want by passing in a vector of values.  \nSee [`to-color`](var:to-color) for information on how to do this.


## Advanced

You can also use the JavaFX Color functions directly.  That will give you ultimate power - including making colors transparent, and doing number-calculations.  

*Examples:*
```
(Color/color 0 0 1) ;; blue
(Color/color 0.0 0.0 1.0) ;; the same blue
(Color/color 0.0 0.0 0.0 0.5) ;; semi-transparent blue 
(Color/rgb 0 0 255) ;; again blue
```
You can read the complete documentation online: [JavaFX Color](https://docs.oracle.com/javase/8/javafx/api/javafx/scene/paint/Color.html)")


(def topic-clojure
  "# Clojure

The underlying programming language for the Turtle API (and for all of George) is Clojure.

Clojure is buildt into the system, which means you can \"dip down\" and do pretty much anything you want that you can do with Clojure.

See [Clojure Cheatsheet](https://clojure.org/api/cheatsheet) for an overview of all available \"commands\" - aka functions, macros, and special forms.")


(def topic-turtles "# Turtles (multiple)

*You can have many turtles at once!*

Standard behavior is for there to be minimum 1 turtle on screen.
If you call any of the standard turtle-commands without a specific turtle as first argument, then `(turtle)` is called. 
See [`turtle`](var:turtle) for more on how this command behaves.

You can create more than one turtle.  It is up to you to \"hold on to\" turtles so you can reference them later.
If you have a reference to a specific turtle, then you can use `with-turtle` to \"bind\" it as the turtle to be applied to turtle commands.  See [`with-turtle`](var:with-turtle).  Or you can pass it as the first argument to standard turtle commands.

You can get a list containing all registered turtles with the command [``]


...
   But all turtle commands can also be applied to a specific turtle - either by \n

")


(def topics 
  {:Welcome              topic-welcome 
   :Color                topic-color
   :Clojure              topic-clojure
   :Turtles              topic-turtles
   (keyword (str *ns*))  ((meta *ns*) :doc)}) ;(meta (find-ns (symbol (str *ns*))))


(def headings 
  {"Turtle" 
   "# Turtle\n\nBasic commands for the turtle."
   "Pen" 
   "# Pen\n\nCommands related to the turtle's pen."
   "Screen" 
   "# Screen\n\nCommands related the screen itself."
   "Utils" 
   "# Utilities \n\nCustom utility Clojure commands in the turtle API.\n\nSee topic [Clojure](:Clojure) for more information.\n"
   "Advanced" 
   "# Advanced\n\nMore advanced turtle commands."
   "Demos"
   "# Demos\n\nFun or interesting demonstrations of Turtle Geometry."
   "Topics"
   "# Topics\n\nIn-depth on certain topics of interest."})

(def turtle-API-list
  ["Turtle"
   #'forward
   #'backward
   #'left
   #'right
   #'home
   #'show
   #'hide
   #'set-visible
   #'is-visible
   #'set-speed
   #'get-speed
   "Pen"
   #'pen-up
   #'pen-down
   #'set-down
   #'is-down
   #'set-color
   #'get-color
   #'set-width
   #'get-width
   #'set-round
   #'is-round
   "Screen"
   #'clear
   #'reset
   #'screen
   #'set-background
   #'get-background
   #'set-axis-visible
   #'is-axis-visible
   #'set-border-visible
   #'is-border-visible
   "Utils"
   #'rep
   #'sleep
   "Advanced"
   #'set-heading
   #'get-heading
   #'set-position
   #'get-position
   #'set-name
   #'get-name
   #'get-state
   #'turtle
   #'new-turtle
   #'with-turtle
   #'get-all-turtles
   #'delete-turtle
   #'delete-all-turtles
   #'set-prop
   #'get-prop
   #'get-props
   #'swap-prop
   #'to-color
   "Demos"
   #'run-sample
   "Topics"
   :Color
   :Clojure
   :Turtles])