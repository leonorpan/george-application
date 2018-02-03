;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle.adhoc.jf4k
  ^{
    :doc "Code snippets used in JFocus4Kids in Stockholm 2018-02-04"}
    
  (:require
    [george.turtle :refer :all]
    [george.turtle.aux :as aux]))


(defn jump [d]
  (let [down? (is-down)]
    (pen-up)
    (forward d) 
    (set-down down?)))


(defn jump-to
  "Same as 'jump', but with a position arg in stead of a distance. "
  [pos]
  (let [down? (is-down)
        orig-heading (get-heading)]
    (pen-up)
    (turn-to pos)
    (move-to pos)
    (set-down down?)
    (set-heading orig-heading)))


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
 

;;; 


(defn jump [d]
  (pen-up) (forward d) (pen-down))

(defn jump-home []
  (pen-up) (home) (pen-down))

(defn center* [w c]
  (set-width w) (set-color c) (forward 0.1) (home))

(defn center []
  (center* 100 :black)
  (center* 85 :white)
  (center* 70 :black)
  (center* 55 :white)
  (center* 40 :black))

(defn pedals* [w c d]
  (set-width w) (set-color c)
  (dotimes [i 8]
    (left (* i 45)) (jump d) (forward 0.1) (jump-home)))

(defn pedals []
  (pedals* 60 :pink 56)
  (pedals* 50 :hotpink 53)
  (pedals* 40 :deeppink 50))


(defn grass []
  (set-color :green)
  (jump-to [-280 -200])
  (set-speed 10)
  (rep 90
       (let [len (+ 50 (rand-int 80))]
         (set-heading 88)
         (forward len)
         (set-heading 92)
         (backward len)))
  (set-speed 1))
;(grass)

(defn stem []
  (set-color :green)
  (set-width 10)
  (jump-to [0 -200])
  (move-to [0 0]))  
;(stem)

(defn flower []
  (reset)
  (set-round true)
  (grass)
  (stem)
  (set-speed 1)
  (pedals)
  (center))
;(flower)


;;;;;

(defn walk []
  (forward (get-prop :step)))

(defn walker []
  (reset)
  (to-front)
  (set-fence :wrap)
  (set-prop :step 0)
  (set-onkey [:LEFT]  #(left 10))
  (set-onkey [:RIGHT] #(right 10))
  (set-onkey [:UP]    #(swap-prop :step inc))
  (set-onkey [:DOWN]  #(swap-prop :step dec))
  (set-onkey [:H]     #(do (set-prop :step 0) (home)))
  (set-onkey [:C]     #(do  (clear)))
  (set-onkey [:U]     #(do  (pen-up)))
  (set-onkey [:D]     #(do  (pen-down)))
  (set-onkey [:S]     #(do  (set-prop :step 0)))
  (set-onkey [:SHIFT :R]     #(do  (set-color :red)))
  (set-onkey [:SHIFT :B]     #(do  (set-color :black)))
  (set-onkey [:SHIFT :DIGIT1]     #(do  (set-width 1)))
  (set-onkey [:SHIFT :DIGIT2]     #(do  (set-width 2)))
  (set-onkey [:SHIFT :DIGIT3]     #(do  (set-width 4)))
  (set-onkey [:SHIFT :DIGIT4]     #(do  (set-width 8)))
  (set-onkey [:SHIFT :DIGIT5]     #(do  (set-width 16)))
  (set-onkey [:SHIFT :DIGIT7]     #(do  (set-width 32)))
  (set-onkey [:SHIFT :DIGIT8]     #(do  (set-width 64)))

  (set-ticker walk)
  (start-ticker))
;(walker)
