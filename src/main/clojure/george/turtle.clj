(ns

    ;; Yes, I wrote this.
    ^{:author "Terje Dahl"}

    ;; The namespace of this module
    george.turtle
    (:require
        ;; lots of useful stuff here
        [george.javafx :as fx]
        :reload
        [george.java :as j]
        :reload
        [clojure.core.async :refer [chan >! <! go go-loop >!! <!! thread timeout]]
        )
    ;(:import [javafx.scene.transform Rotate])
    (:import [javafx.scene.transform Rotate]))


;(j/import! fx/classes)
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
               (fx/stage
                   :title "Turtle"
                   :width 600
                   :height 600
                   :scene (fx/scene
                              root
                              :fill fx/WHITESMOKE)
                   :onclose #(reset! screen-singleton nil))

          ]
        (doto root
            ;(. setLayoutX 200)
            (-> .layoutXProperty (. bind (-> stage .widthProperty (. divide 2))))
            ;(. setLayoutY 200)
            (-> .layoutYProperty (. bind (-> stage .heightProperty (. divide 2))))
        ;    (. setRotationAxis Rotate/X_AXIS)
        ;    (. setRotate 180.)
            ;(. setRotationAxis Rotate/Z_AXIS)
            )
        {:root root :stage stage}))

;; Creates and show a new screen, or brings the existing screen to front
(defn- screen []
        (if @screen-singleton
            (-> @screen-singleton :stage .toFront)
            (reset! screen-singleton(create-screen))))


#_(defn- render [pen]
    (doto (:node pen)
        (. setRotate (:angle pen))
        (. setLayoutX (:x pen))
        (. setLayoutY (-(:y pen)))
        )
    (println (-> pen :node .getRotate))

    pen)


(defn- add-node [screen node]
    (fx/add (:root screen) node))



(defn angle [pen]
    (. (:node pen) getRotate))

(defn x [pen]
    (. (:node pen) getLayoutX))

(defn y [pen]
    (. (:node pen) getLayoutY))



#_(defn set-angle [pen-node new-angle]
    (. pen-node setRotate (-  new-angle))
    pen-node)

#_(defn inc-angle [pen-node degrees]
    (set-angle pen-node (+ (angle pen-node) degrees))
    pen-node)



(defn start-dequeue-loop [pen]
    (let [done-chan (chan)]
        (go (while true
                (let [f (<!! (:chan pen))]
            (f done-chan)
            #_(condp = cmd
                :position (do (printf "[%s %s]\n" (x pen) (y pen)) (go (>! done-chan true)))
                :heading (do (println (angle pen) "deg") (go (>! done-chan true)))
                )
            (<!! done-chan))))))



;;;; API ;;;;


#_(defn home [pen]
      (assoc pen :x 0. :y 0.))


#_(defn clear [pen]
      (println "NOT IMPLEMENTED: clear")
      (let [
            scrn (screen)
            ]
          pen))


#_(defn reset [pen]
      (-> pen clear home))



(def exec-fn (j/singlethreadexecute-fn))


(defn- enqueue [pen f]
    (exec-fn #(>!! (:chan pen) f))
    pen)

(defn color [pen color]
    (enqueue pen (fn [done-chan] (reset! (:color pen) color)(go (>! done-chan true)))))


(defn down
    ([pen]
     (down pen true))
    ([pen b]
     (enqueue pen (fn [done-chan] (reset! (:down pen) b)(go (>! done-chan true)))))
 )

(defn up [pen]
    (down pen false))


(defn left
    [pen degrees]
    (enqueue pen
        (fn [done-chan]
            (let [new-angle (+ (angle pen) degrees)]
                (doto
                    (fx/simple-timeline
                        (* (/ (Math/abs degrees) 360) 1000)
                        #(>!! done-chan true)
                        [(. (:node pen) rotateProperty) new-angle]
                        )
                    .play)))))


(defn right [pen degrees]
    (left pen (- degrees)))


(defn forward
    [pen distance]
    ;(queue-command pen [:move distance])
    (enqueue pen
        (fn [done-chan]
            (let [ang (angle pen)
                  x (x pen)
                  y (y pen)
                  line (if @(:down pen) (fx/line :x1 x :y1 y :color (Color/web @(:color pen))))
                  rad (Math/toRadians ang)
                  x-fac (Math/cos rad)
                  y-fac (Math/sin rad)
                  new-x (+ x (* distance x-fac))
                  new-y (+ y (* distance y-fac))
                  node (:node pen)
                  ]
                (if line
                    (fx/later
                        (add-node (:screen pen) line)
                        (. node toFront)
                        ))
                (doto
                    (fx/simple-timeline
                        (* (/ (Math/abs distance) 300) 1000)

                        #(>!! done-chan true)

                        [(. node layoutXProperty) new-x]
                        [(. node layoutYProperty) new-y]
                        (if line [(. line endXProperty) new-x])
                        (if line [(. line endYProperty) new-y])
                        )
                    .play)))))





(defn backward [pen distance]
    (forward pen (- distance)))


#_(defn position
    [pen]
    (queue-command pen [:position]))

#_(defn heading
    [pen]
    (queue-command pen [:heading]))


        ;; Creates a new pen and sets it on the screen.
;; If a screen doesn't exist, then one will be created.
(defn pen []
        (let [s (screen)
              n   (pen-node)
              c (chan)
              p {:node n :chan c :screen s :down (atom true) :color (atom "black")}
              ]
            (add-node s n)
            (Thread/sleep 500)
            (start-dequeue-loop p)
            p))


;;;; run ;;;;

(fx/later
    (let [p (pen)]
        (color p "red")
        (dotimes [_ 20]
            (-> p (forward 100) (left 170)))
        (-> p up (left 120) (forward 200) down (color "blue"))
        (dotimes [_ 4]
            (-> p (right 90) (forward 50)))
    ))
