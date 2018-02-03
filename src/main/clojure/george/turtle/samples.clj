;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle.samples
  (:require 
    [george.turtle :refer :all]
    [george.turtle.aux :as aux]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)


;;;;; multi-tree

(defn- red [^double len]
  (* (+ 0.5 (* 0.1 ^int (rand-int 5))) len))

(defn- col [^double len]
  (if (< len 10) "green" "brown"))

(defn- ang []
  (+ 10 ^int (rand-int 45)))

(defn- wid [^double len]
  (let [w (/ len 15.)]
    (if (< w 1) 1 w)))

(defn- line [t ^double len]
  (set-width t (wid (Math/abs len)))
  (set-color t (col (Math/abs len)))
  (forward t len))

(defn- tree [t ^double len]
  (when (> len 2)
    (line t len)
    (let [t1 (clone-turtle t)
          t2 (clone-turtle t)]
      (future
        (left t1 (ang))
        (tree t1 (red len)))
      (future
        (right t2 (ang))
        (tree  t2 (red len)))))
  (delete-turtle t))


(defn multi-tree 
  "*May a thousand turtles bloom*  
      
  * * *

  *Learn how to build this animation yourself!*   
  Improve it. Share it. 
  Then go create something new and amazing.  
  Its in [\"George - the book\"](http://www.george.andante.no/book)."
  [& [len]]
  (screen [700 450])
  (reset)
  (left 90)
  (pen-up) 
  (forward (- (* 2 (double (or len 50))))) 
  (pen-down)
  (set-speed 1)
  (tree (turtle) (or len 50)))



;;;;;;;; asteroids


(def SCORE_LIFE_POS [-280 200])


(defn-  spaceship-shape []
  ;; Could be implemented with a group of lines in stead
  (aux/new-polygon [[-8 5] [8 0] [-8 -5]]
                   :color :white))
  

(defn- shot-shape []
  ;; Could be implemented with a line in stead
  (aux/new-rectangle :size [2 1] 
                     :color :white))


(defn- rock-shape [size]
  ;; Better design with a few "random" polygon designs
  (aux/new-rectangle
    :size ({3 [50 50] 2 [30 30] 1 [15 15]} size)
    :color :white))


(defn rand+- []
 (rand-nth [1 -1]))


(defn- new-rock [loc ^long size]
  (let [three? (= size 3)]
    (new-turtle
      :name :rock
      :down false 
      :node (rock-shape size)
      :position loc
      :heading (if three?
                   (+ ^int (rand-nth [0 90 180 280]) (* ^int (rand-int 30) ^int (rand+-)))
                   (rand-int 360))
      :props {:size size
              :step (if three?  1 (rand-nth [1 1.2  1.7 2.8]))
              :points (case size 
                        3 20 
                        2 50 
                        1 100)})))


(defn- make-rocks []
  (rep 5
       (let [x (* (+ ^int (rand-int 100) 100) ^int (rand+-))
             y (* (+ ^int (rand-int 100) 100) ^int (rand+-))]
         (new-rock [x y] 3))))


(defn- make-rocks-maybe [^long life-count rocks rock]
  (when (and (> life-count 0)
             (= (count rocks) 1)
             (= (get-prop rock :size) 1))
    (make-rocks)))


(defn- new-life-turtle [^long life-index]
  (doto
    (new-turtle :name :life 
                :down false 
                :speed nil 
                :heading 90 
                :node (spaceship-shape))
    (move-to (let [[^int x ^int y] SCORE_LIFE_POS]
               [(+ x 8 (* life-index 16))
                (- y 32)]))))


(defn- update-life-count [life-count_ diff]
  (->> (get-all-turtles)
       (filter #(= (get-name %) :life))
       (mapv delete-turtle))
  (dotimes [i (swap! life-count_ + diff)]
    (new-life-turtle i)))


(defn- update-score
  [score-turtle points]
  (when points
        (doto score-turtle
          (swap-prop :score #(+ ^int % points))
          (undo)))
  (write score-turtle (get-prop score-turtle :score) false))


(defn- new-score-turtle []
  (doto
    (new-turtle
      :name :score
      :down false
      :speed 10
      :visible false
      :color :white
      :font ["Source Code Pro" 24]
      :undo 2
      :props {:score 0})
    (move-to SCORE_LIFE_POS)
    (update-score nil)))


(defn-  blast-rock [rock scorekeeper]
  (let [^int size (get-prop rock :size)
        loc (get-position rock)
        points (get-prop rock :points)]
    (delete-turtle rock)
    ;; TODO: add explosion ?
    (when (> size 1)
          (rep 2 (new-rock loc (dec size))))
    (update-score scorekeeper points)))


(defn- thrust [physics spaceship]
  (when (is-visible spaceship)
    (let [physics-speed  (get-prop physics :step)
          physics-heading (get-heading physics)
          spaceship-heading (get-heading spaceship)
          [^double new-speed new-heading] 
          (aux/add-vectors [physics-speed physics-heading]
                           [0.25 spaceship-heading])]
      (doto physics
        (set-prop :step (min new-speed 8))    
        (set-heading new-heading)))))


(defn- break [physics]
  (swap-prop physics :step #(max 0 (- ^double % 0.25))))


(defn- drag [physics]
  (swap-prop physics :step #(max 0 (- ^double % 0.001))))


(defn- turn [spaceship degrees]
  (when (is-visible spaceship)
    (left spaceship degrees)))


(defn- shoot [physics spaceship]
  ;; When keeping key pressed down, a stream fo shots is release. Could be prevented with some sort of timer. 
  (when (is-visible spaceship)
    (new-turtle
      :name     :shot
      :down     false
      :speed    nil
      :position (get-position physics)
      :heading  (get-heading spaceship)
      :node     (shot-shape)
      :props    {:step 10})))


(defn- write-game-over []
  (with-turtle
    (new-turtle
      :visible false
      :color :white
      :position [-100 16]
      :font ["Source Code Pro" 32])
    (write "game over")))


(defn- set-keys [physics spaceship]
  (set-onkey [:UP]           #(thrust physics spaceship))
  (set-onkey [:DOWN]         #(break physics))
  (set-onkey [:LEFT]         #(turn spaceship 10))
  (set-onkey [:RIGHT]        #(turn spaceship -10))
  ;; When shooting, the other key-downs are disabled.  
  ;; Hard to fix while maintaining a simple interface.
  ;; Would require some sort of custom EventHandler to pass to the javafx keyevent-functions.
  (set-onkey [:SPACE] #(shoot physics spaceship))
  (set-onkey [:P]     #(if (is-ticker-running) (stop-ticker) (start-ticker))))


(defn blink-in [spaceship]
  (hide spaceship)
  (let [blinker (clone-turtle spaceship)]
    (dotimes [i 10]
      (set-visible blinker (even? i))    
      (sleep 100))
    (delete-turtle blinker))
  (show spaceship))


(defn- game-loop [physics spaceship scorekeeper life-count_]
  (doseq [t (get-all-turtles)]
    (with-turtle t

      (when-let [step (get-prop :step)]
        (forward step))
                 
      (set-position spaceship (get-position physics))
      
      (drag physics)
                 
      (let [rocks 
            (filter #(= (get-name %) :rock)  
                    (get-all-turtles))]

        ;; hit
        (when (= (get-name) :shot)
          (when-let [rock (first (get-overlappers t rocks))]
            (delete-turtle t)
            (blast-rock rock scorekeeper)
            (make-rocks-maybe @life-count_ rocks rock)))

        ;; death
        (when (and (= t physics) (is-visible spaceship))
          (when-let [rock (first (get-overlappers t rocks))]

            (hide spaceship)
            ;; TODO: add explosion ?
            (blast-rock rock scorekeeper)
            (update-life-count life-count_ -1)
            (if (> ^int @life-count_ 0)
              (do
                  (make-rocks-maybe @life-count_ rocks rock)
                  (doto physics
                    (set-prop :step 0)
                    (home))
                  (doto spaceship
                    (home)
                    (set-heading 90)
                    (future (blink-in spaceship))))
              (write-game-over))))))))


(defn asteroids 
  "Yes, the classic.  

  Not prefect, but the gameplay is pretty close.
  Read about Asteroids on [Wikepedia](https://en.wikipedia.org/wiki/Asteroids_%28video_game%29).
    
  Try the original version online at [FreeAsteroids.org](http://www.freeasteroids.org/).

  * * *

  *Learn how to build this game yourself!*  
  Improve it. Share it. 
  Then build other cool games.  
  Its in [\"George - the book\"](http://www.george.andante.no/book).
  "
  []
  (reset false)
  (set-background :black)
  (to-front)
  
  (let [spaceship
        (new-turtle
          :name :spaceship
          :down false
          :node (spaceship-shape)
          :heading 90
          :visible false)
        
        physics
        (new-turtle
          :name :physics
          :down false
          :visible false
          :props {:step 0})
 
        scorekeeper
        (new-score-turtle)

        life-count_ 
        (atom 3)]

    (update-life-count life-count_ 0)

    (set-keys physics spaceship)

    (set-fence
      {:fence   (fn [turtle] (if (= (get-name turtle) :shot) :stop :wrap))
       :onfence (fn [turtle _] (when (= (get-name turtle) :shot)
                                     (delete-turtle turtle)))})

    (make-rocks)
    (set-ticker 
      #(game-loop physics spaceship scorekeeper life-count_))
    (start-ticker)
    (blink-in spaceship)))


;; TODO: add better spaceship-design?
;; TODO: add better rock-design?
;; TODO: Thrust indicator (jet)
;; Missing: Extra lives
;; Missing: UFOs
;; Missing: The full arcade experience: "New game", High score, etc.

;(asteroids)