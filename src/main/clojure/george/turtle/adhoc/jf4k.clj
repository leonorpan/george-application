;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle.adhoc.jf4k
  ^{
    :doc "Code snippets used in JFocus4Kids in Stockholm 2018-02-04"}   
  (:require [george.turtle :refer :all]))


;; code for starry night sky

(defn jump [d]
    (pen-up) (forward d)  (pen-down))


(defn star [size & [color]]
  (let [c (get-color)]
    (set-color (or color :orange))
    (rep 5 (forward size) (left 144))
    (set-color c)))


(defn stars 
  "A beautiful starry night."
  []
  (reset)
  (set-background :black)
  (set-speed 10)
  (rep 100
    (home)
    (left (rand-int 360)) 
    (jump (rand-int 200))
    (star (rand-int 20) :orange))
  (hide))
;(stars)
 

;;;;;

(defn walk 
  "This command is called every tick. It moves the turtle forward whatever value is in ':step'"
  []
  (forward (get-prop :step)))

(defn walker []
  (reset)
  (to-front)  ;; This is to make sure the screen gets focus.
  (set-fence :wrap)  ;; This ensures that the turtle doesn't disappear of the screen
  (set-prop :step 0)  ;; We start with the turtle standing still
  ;; Left or right arrows will turn the turtle a little
  (set-onkey [:LEFT]  #(left 10))
  (set-onkey [:RIGHT] #(right 10))
  ;; Up or down arrows will speed up or slow down the turtle.
  (set-onkey [:UP]    #(swap-prop :step inc))
  (set-onkey [:DOWN]  #(swap-prop :step dec))
  ;; If we want to do more than one thing, put your commands inside a 'do' command.
  (set-onkey [:H]     #(do (set-prop :step 0) (home)))
  ;; And here are some other ideas for commands you can add.
  (set-onkey [:C]     #(clear))
  (set-onkey [:U]     #(pen-up))
  (set-onkey [:D]     #(pen-down))
  (set-onkey [:S]     #(set-prop :step 0))
  (set-onkey [:R]     #(set-color :red))
  (set-onkey [:B]     #(set-color :black))
  (set-onkey [:SHIFT :B]     #(set-color :blue))
  (set-onkey [:DIGIT1]     #(set-width 1))
  (set-onkey [:DIGIT2]     #(set-width 2))
  (set-onkey [:DIGIT3]     #(set-width 4))
  (set-onkey [:DIGIT4]     #(set-width 8))
  (set-onkey [:DIGIT5]     #(set-width 16))
  (set-onkey [:DIGIT7]     #(set-width 32))
  (set-onkey [:DIGIT8]     #(set-width 64))
  ;; Now set the thicker so that 'walk' will be called every tick.
  (set-ticker walk)
  ;; finally, start the ticker. 
  (start-ticker))
;(walker)
