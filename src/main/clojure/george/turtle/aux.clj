;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle.aux
  (:require
    [george.javafx :as fx]
    [george.javafx.java :as fxj])
  (:import
    [javafx.scene.text Font]
    [javafx.scene.paint Color Paint]
    [javafx.scene.shape Polygon]))
    

;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)



;; https://www.mathsisfun.com/algebra/vectors.html


(defn hypotenuse [^double x ^double y]
  (Math/sqrt (+ (* x x) (* y y))))


(defn to-cartesian
  "Adapted vector function.
  'length', a.k.a. radius.
  'heading', a.k.a. theta.  
  Returns a 2-element vector [x y] -  the x and y lengths of the passed in polar.
  
  **OBS!** 'heading' is in degrees! (Not radians)
  "
  [[^double length ^ double heading]]
  (let [theta (Math/toRadians heading)
        x (* (Math/cos theta) length)
        y (* (Math/sin theta) length)]
    [x y]))



(defn to-polar
  "Adapted vector function.
  Returns a 2-element vector [length heading] -  the radius and theta of the passed-in cartesian.

  **OBS!** 'heading' is in degrees! (Not radians)
  "
  
  [[x y]]
  (let [length (hypotenuse x y)
        theta (Math/atan2 y x)
        heading (Math/toDegrees theta)]
    [length heading]))

;(println (to-polar [100 100]))
;(println (to-polar [-100 100]))
;(println (to-cartesian [100 45]))
;(println (to-cartesian [100 135]))


(defn- add-vectors* [v1 v2]
  (let [[x1 y1] (to-cartesian v1)
        [x2 y2] (to-cartesian v2)]
    (to-polar [(+ ^double x1 ^double x2) (+ ^double y1 ^double y2)])))


(defn add-vectors 
 "Adds 1 or more vectors of form [length heading] - where heading is in degrees."
  
  ([v1] v1)
 ([v1 v2 & more]
  (let [v (add-vectors* v1 v2)]
    (if (first more)
      (apply add-vectors (cons v more))
      v))))

;(println (add-vectors [100 45]))
;(println (add-vectors [100 45] [100 45]))
;(println (add-vectors [100 45] [100 45] [100 45]))
;(println (add-vectors [100 45] [100 135] [100 270]))




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
  (assert (not (nil? color))
          "'color' may not be `nil`.")
  (cond
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


(defn to-font 
  "See [`set-font`](var:set-font) for more info."
  [font]
  (assert (not (nil? font))
          "'font' can not be set to 'nil'.")
  ;; Easy basic assertions.  But not good enough.  Will cause difficult bugs or errors.
  (cond
    (instance? Font font)
    font
    ;; TODO: check content of font vector better
    (vector? font)
    (apply fx/new-font font)
    :default
    (fx/new-font font)))
;(println (to-font "Arial"))
;(println (to-font ["Arial"]))
;(println (to-font 12))
;(println (to-font [12]))
;(println (to-font ["Arial" 12]))
;(println (to-font ["Source Code Pro" 12]))
;(println (to-font ["Arial" :normal 12]))
;(println (to-font ["Geneva" :bold :italic 12]))
;(println (to-font (fx/new-font 16)))
;(to-font nil)  ;; fail


(defn flip-Y
  "Takes a position [x y] and inverts y - for mapping turtle-coordinates to JavaFX coordinates."
  [[x ^double y]]
  [x (- y)])


(defn new-rectangle
  "A lower level function. Returns a JavaFX Rectangle.
  This is used mainly for replacing the default node of the turtle.

*Example:*
```
(new-rectangle :size [50 50] 
               :heading 30 
               :color :black 
               :fill :red) ;returns a 50x50 rectangle, tilted 30 degrees, with black border and red fill.
```  
  "
  [& {:keys [size position heading arc color width fill]
      :or   {size     [10 10]
             position [0 0]
             heading  0
             arc      0
             color    fx/ANTHRECITE
             width    1
             fill     Color/TRANSPARENT}}]
  (doto (fx/rectangle
            :size size
            :location (flip-Y position)
            :rotation (- ^double heading)
            :arc arc
            :fill (to-color fill))
      (fx/set-stroke (to-color color) width)))


(defn new-polygon
  "A lower level function. Returns a JavaFX Polygon.  (A multi-sided/multi-angled shape).
  This is used mainly for replacing the default node of the turtle.

  'points' - a vector of 3 or more [x y] point vectors.
  
*Example:*
```
(new-polygon [[0 0] [10 20] [-10 20]] 
             :color :black 
             :fill :red) ;returns triangle, pointing down, with black border and red fill.
```  
"
  
  ;; We use a vector of vectors - in stead of a free list of number for readability, consistency, and easier code processing.
  [points & {:keys [position heading arc color width fill]
             :or   {position [0 0]
                    heading  0
                    arc      0
                    color    fx/ANTHRECITE
                    width    1
                    fill     Color/TRANSPARENT}}]
  ;; TODO: assert 'points'

  (doto (Polygon. (fxj/vargs-t* Double/TYPE (flatten (map flip-Y points))))
        (.setFill (to-color fill))
        (fx/set-stroke (to-color color) width)
        (.setRotate (- ^double heading))
        (fx/set-translate-XY position)))

