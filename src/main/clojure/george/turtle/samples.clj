;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(in-ns 'george.turtle)


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
  "*May a thousand turtles bloom*"
  []
  (screen [700 450])
  (reset)
  (left 90)
  (pen-up) (forward -200) (pen-down)
  (set-speed 1)
  (tree (turtle) 100))

