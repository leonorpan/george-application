(ns george.turtle
    (:require [george.javafx :as fx] :reload))

(fx/import-classes!)

;; Creates and returns a new graphical object of a pen/turtle
(defn- pen-node []
    (fx/polygon
                 5. 0.
               -5. 5.
                -3. 0.
               -5 -5.
               :fill fx/ANTHRECITE)
    )


;; Will hold a reference to the one (and only) screen
(def ^:private screen-singleton (atom nil))


;; creates and returns a new  visible screen
(defn- create-screen []
    (let [
          root (fx/group)
          stage
               (fx/now (fx/stage
                   :title "Turtle"
                   :width 600
                   :height 600
                   :scene (fx/scene root :fill fx/WHITESMOKE)
                   :onclose #(reset! screen-singleton nil)))
          ]
        (doto root
            (-> .layoutXProperty (. bind (-> stage .widthProperty (. divide 2))))
            (-> .layoutYProperty (. bind (-> stage .heightProperty (. divide 2))))
            )
        {:root root :stage stage}))


;; Creates and show a new screen, or brings the existing screen to front
(defn- screen []
        (if @screen-singleton
            @screen-singleton
            (reset! screen-singleton (create-screen))))


(defn- add-node [screen node]
    (fx/now (fx/add (:root screen) node)))



;;;; API ;;;;


(defn angle [pen]
    (. (:node @pen) getRotate))


(defn x [pen]
    (. (:node @pen) getLayoutX))


(defn y [pen]
    (. (:node @pen) getLayoutY))


(defn color [pen color]
    (swap! pen assoc :color color))


(defn down
    ([pen]
     (down pen true))
    ([pen b]
     (swap! pen assoc :down b)
        pen)
 )

(defn up [pen]
    (down pen false))


(defn left [pen degrees]
    (let [new-angle (+ (angle pen) degrees)]
        (fx/synced-keyframe
            (* (/ (Math/abs degrees) (* 3 360)) 1000)  ;; 3 rotations pr second
            [(. (:node @pen) rotateProperty) new-angle]
            )
        pen ))


(defn right [pen degrees]
    (left pen (- degrees)))


(defn forward
    [pen distance]
     (let [ang (angle pen)
           x (x pen)
           y (y pen)
           line (if (:down @pen) (fx/line :x1 x :y1 y :color (Color/web (:color @pen))))
           rad (Math/toRadians ang)
           x-fac (Math/cos rad)
           y-fac (Math/sin rad)
           new-x (+ x (* distance x-fac))
           new-y (+ y (* distance y-fac))
           node (:node @pen)
           ]
         (when line
                 (add-node (:screen @pen) line)
                 (fx/later (. node toFront)))

         (fx/synced-keyframe
             (* (/ (Math/abs distance) 600) 1000)  ;; 600 px per second
             [(. node layoutXProperty) new-x]
             [(. node layoutYProperty) new-y]
             (if line [(. line endXProperty) new-x])
             (if line [(. line endYProperty) new-y])
             )
             pen ))



;; Creates a new pen and sets it on the screen.
;; If a screen doesn't exist, then one will be created.
(defn pen []
        (let [screen (screen)
              node  (pen-node)
              ]
            (add-node screen node)
            (Thread/sleep 300)
            (atom {:screen screen :node node :down true :color "black"})))

(defn clear [pen]
    (println "ERROR: 'clear' not implemented (yet)."))

(defn home [pen]
    (println "ERROR: 'home' not implemented (yet)."))

(defn reset [pen]
    (println "ERROR: 'reset' not implemented (yet)."))

(defn backward [pen]
    (println "ERROR: 'backward' not implemented (yet)."))

(defn hide [pen]
    (println "ERROR: 'hide' not implemented (yet)."))

(defn show [pen]
    (println "ERROR: 'show' not implemented (yet)."))

;;;; run ;;;;


(let [p (pen)]

    (color p "red")

    (dotimes [_ 37]
        (-> p (forward 100) (left 170)))

    (-> p up (right 170) (right 90) (forward 200) down (color "blue"))

    (dotimes [_ 4]
        (-> p (right 90) (forward 100)))

    (pen)
    )
