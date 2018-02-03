;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle.tom
  (:require
    [george.turtle :refer :all]
    [george.turtle.aux :as aux]))


;(defn- hello [turtle]
;  (let [n (:name @turtle)]
;    (println (format "Hello.  My name is %s.  %s the Turtle." n n))
;    nil))


(defn jump 
  "Does 'forward' with pen up, and setting the pen down if it was down."
  ;; TODO: This one is so common that maybe it should become part of the API? Or at least inserted into 'aux'?  
  [d]
  (let [down? (is-down)]
    (pen-up)
    (forward d)
    (set-down down?)))


(defn skip
  "Same as 'jump', but sideways (to the left)."

  [d]
  (let [down? (is-down)]
    (pen-up)
    (left 90)
    (forward d)
    (right 90)
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


(defn jump-to-and 
  "Same as 'jump-to', but also leaves the turtle with a new heading."
  [pos head]
  (jump-to pos)
  (set-heading head))


(defn- shell 
  "Draws Tom's shell.  'pos' is the point of the tail end of the shell.  'head' is the angle of the shell."
  [pos head]
  (let []
    (jump-to-and pos head)
    
    (set-round true)
    (set-color :darkgreen)

    ;; outline
    (set-width 5)
    (arc-left 400 30)
    (left 70)
    (arc-left 104 190)
    (left 110)
    ;; structure
    (set-color [:darkgreen 0.5])
    (set-width 3)
    (jump-to-and pos head) (jump 30) (skip 30) (left 8) (arc-left 400 20)
    (jump-to-and pos head) (jump 35) (skip 60) (left 10) (arc-left 350 18)
    (jump-to-and pos head) (jump 45) (skip 25) (left 110) (arc-right 100 30)
    (jump-to-and pos head) (jump 100) (skip 40) (left 100) (arc-left 100 40)
    (jump-to-and pos head) (jump 150) (skip 55) (left 110) (arc-left 95 40)
    ;; back to start
    (jump-to-and pos head)))


(defn tom []
  (println "Hello. My name is Tom. Tom the turtle."))


(defn- head 
  "Draws Tom's head.  'pos' is the base of the throat. 'head' is the angle of the head."
  [pos head]
  (let [offset 2]
    (jump-to-and pos head)

    (set-round true)
    (set-color :seagreen)

    (set-width 3)
    ;; neck and jaw
    (jump offset)
    (left 10)
    (forward 10)
    (arc-right 70 30)
    (arc-left 80 50)
    (arc-left 40 35)
    ;; mouth, nose, forehead neck 
    (jump-to-and pos head)
    (jump offset)
    (jump 90) (skip 20)
    (arc-left 100 20)
    (arc-left 50 40) 
    (arc-left 30 10) 
    (arc-left 50 60) 
    (arc-left 25 40) 
    (arc-left 50 50) 
    (arc-left 150 10) 
    (arc-right 300 14)
    (arc-right 100 15)
    ;; eye and brow
    (jump-to-and pos head)
    (jump offset)
    (jump 110) (skip 70)
    (arc-left 4 400) ;; eye
    (forward 1) (arc-left 1.5 360) (forward -1)  ;; pupil
    (skip 12)
    (left 60)
    (arc-right 7 90)  ;; brow
    ;; nostril
    (jump-to-and pos head)
    (jump offset)
    (jump 140) (skip 60)
    (right 40)
    (arc-left 5 60)

    ;; crook
    (jump-to-and pos head)
    (jump 5)
    (jump 85) (skip 20)
    (left 120)
    (arc-right 8 75)
    ;; back to start
    (jump-to-and pos head)))

(defn tail [pos]
  (let [offset 5]
    (jump-to pos)
    (left 200)
    (jump offset)
    (arc-right 25 60)
    (left 170)
    (arc-left 25 75)))


(defn leg [pos head]
  (jump-to-and pos head)
  (right 45)
  (arc-right 50 30)
  (arc-right 10 10) ;; knee
  (arc-right 50 30)
  (left 90)
  (forward 10)
  (right 120) ;; toe
  (arc-right 25 60)
  (right 85) ;; heal
  (arc-left 50 30)
  (arc-left 10 10)
  (arc-left 45 31))


(defn knee [pos head]
  (jump-to-and pos head)
  (right 45)
  (arc-right 50 20)
  (arc-right 10 40) ;; knee
  (arc-right 50 20)
  (left 90)
  (forward 10))


(defn crook [pos head]
  (jump-to-and pos head)
  (right 60)
  (arc-right 50 20)
  (arc-right 10 10) ;; knee
  (arc-right 70 20)
  (left 90)
  (forward 10))


(defn draw-pen [[down? color width :as pen]]
  (let [orig-color (get-color)
        orig-w (get-width)
        orig-pos (get-position)
        orig-head (get-heading)
        w (* width 15) 
        h (* width 50)]
    (set-color color)
    (set-width (* width orig-w))
    (right 90)
    (jump (- (/ h 3.)))
    (rep 2 
         (forward h) (left 90) (forward w) (left 90))    
    (jump h)
    (left 90)
    (rep 3
      (forward w) (right 120))
    
    (set-color orig-color)
    (set-width orig-w)
    (jump-to-and orig-pos orig-head)))


(defn hand-with-pen [pos head speed [down? color  width :as pen]]
  (jump-to-and pos head)
  (set-speed speed)
  (right 45)
  (arc-right 40 20)
  (arc-left 5 100)
  (arc-right 100 20)
  (draw-pen pen)
  (arc-right 5 182) ;; finger tip 1
  (forward 20)
  (right 181)
  (forward 15)
  (arc-right 4 182) ;; finger tip 2
  (forward 12)
  (right 181)
  (forward 8)
  (arc-right 3 180) ;; finger tip 3
  (forward 8)
  (left 30)
  (arc-right 30 60)
  (arc-right 5 20)
  (arc-right 50 30))

(defn draw-tom [& [speed [down? :as pen]]]
  (reset)
  (set-speed speed)
  (shell [-100 -50] 10)
  (head [85 50] 10)
  (tail [-100 -50])
  (leg [-60 -45] -10)
  (knee [-55 -45] 15)
  (crook [50 0] -5)
  (if pen
    (hand-with-pen [70 15] (if down? -15 25) 10 pen)
    (leg [70 15] 0))
  (hide))

;(draw-tom)

(defn draw-tom-with-pen [& [speed pen]]
  (draw-tom speed pen))
;(draw-tom-with-pen 10 [true :black 1])
;(sleep 1000)
;(draw-tom-with-pen 10 [false :black 1])


(defn slide-welcome []
  (reset)
  (jump-to [-100 30])
  (set-font 48)
  (write "Welcome!")  
  (hide))
;(welcome)


(defn slide-meet-tom []
  (reset)
  (jump-to [-50 30])
  (set-font 36)
  (write "Tom")
  (hide))


(defn- slide-tom-first []
  (reset)
  (draw-tom 1)
  (hide))

(defn- slide-tom []
  (reset)
  (draw-tom)
  (hide))

(defn- slide-tom-tiny []
  (reset))

(defn- slide-add-circle []
  (new-turtle :color :red :width 5 :visible false)
  (jump-to-and [-30 0] -90)  
  (arc-left 30 360))


(defn- slide-tom-runs-around [& [down?]]
  (reset)
  (set-down down?)
  (set-speed 1/2)
  (rep 25
       (forward (rand-int 150))
       (left (rand-int 360))))


(defn- slide-tom-toggles-pen-down []
  (reset)
  (set-speed 1/2)
  (dotimes [i 20]
    (set-down (even? i))   
    (forward 20)
    (left 18)))


(defn- slide-tom-pen []
  (reset)
  (draw-tom-with-pen 10 [true :black 1])
  (hide))
;(slide-tom-pen)


(defn- slide-tom-pen-up []
  (reset)
  (draw-tom-with-pen nil [false :black 1])
  (hide))


(defn- slide-tom-pen-red []
  (reset)
  (draw-tom-with-pen nil [true :red 1])
  (hide))

(defn- slide-tom-pen-blue []
  (reset)
  (draw-tom-with-pen nil [true :blue 1])
  (hide))


(defn- slide-tom-pen-blue-bigger []
  (reset)
  (draw-tom-with-pen nil [true :blue 2])
  (hide))

(defn- slide-tom-plays-with-pen []
  (reset)
  (set-color :blue)  (set-width 3)
  (rep 4 (forward 30) (left 90))

  (jump -10) (skip -10)
  (set-color :red)  (set-width 5)
  (rep 4 (forward 50) (left 90))

  (jump -10) (skip -10)
  (set-color :gray)  (set-width 1) (set-speed 10)
  (dotimes [i 200] (forward (+ 70 i)) (left 88))
  (set-speed 1/2)
  (jump-to [15 15]))


(defn slide-first-commands []
  (reset)
  (hide)
  (jump-to [-200 150])
  (set-font 24)
  (write "(reset)")
  (sleep 500)
  
  (let [t1 (turtle)] 
    (new-turtle :width 5 :speed 1/2)
    (with-turtle t1 
                 (skip -30)
                 (write "(forward 100)")
                 (sleep 300))
    (forward 100)
    (sleep 700)
    (with-turtle t1
             (skip -30)
             (write "(left 90)")
             (sleep 300))
    (left 90)
    (sleep 700)
    (with-turtle t1
               (skip -30)
               (write "(forward 200)")
               (sleep 300))
    (forward 200)))


(def slides-tom 
  [slide-welcome
   slide-meet-tom
   slide-tom-first
   slide-tom-tiny 
   slide-add-circle
   slide-tom
   slide-tom-runs-around
   slide-tom-pen
   #(slide-tom-runs-around true)
   slide-tom-pen-up
   slide-tom-toggles-pen-down
   slide-tom-pen-red
   slide-tom-pen-blue
   slide-tom-pen-blue-bigger
   slide-tom-plays-with-pen
   slide-first-commands])


(defn run-slides
  "Takes a list of functions to be run as \"slides\""
 ([slides]
  (reset false)
  (to-front)
  (run-slides slides 1)) 
 ([slides slide-nr]
  (let [slide (get slides (dec slide-nr))
        cnt (count slides)
        next? (< slide-nr cnt)
        prev? (pos? (dec slide-nr))]

    (assert (some? slide) (format "No slide nr %s of %s" slide-nr (count slides)))

    (println (format "Slide nr %s of %s" slide-nr cnt))
    (slide)
    
    (if prev?
      (set-onkey [:LEFT] #(future (run-slides slides (dec slide-nr))))
      (unset-onkey [:LEFT]))
    (if next?
      (set-onkey [:RIGHT] #(future (run-slides slides (inc slide-nr))))
      (unset-onkey [:RIGHT])))))
  
;(set-onkey [:ESCAPE] #(println "exit slides")))
  

(defn run-tom-slides [ & [slide-nr]]
  (run-slides slides-tom (or slide-nr 1)))

;(run-tom-slides 15)