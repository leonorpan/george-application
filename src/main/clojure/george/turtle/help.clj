;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle.help
  (:require
    [clojure.string :as cs]
    [clojure.java.io :as cio]
    [environ.core :refer [env]]
    [george.turtle :refer :all :as tr]
    [george.turtle.samples :as samples]
    [george.util.singleton :as singleton]
    [george.application.ui.styled :as styled]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.application.ui.layout :as layout])
  (:import
    [javafx.stage Stage]
    [javafx.scene Node]
    [javafx.scene.paint Color]
    [javafx.scene.layout VBox]
    [javafx.scene.control ScrollPane]))

;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)




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

")  ;; TODO: Write more here ...


(def topic-properties
  "# Properties
  A turtle has a build-in map which is initially empty, but which can contain anything.
  
  You can use properties for anything you like - i.e. setting the step of individual turtles in a ticker-based animation or game.

  That means you could also use one or more turtles as pure data-stores, making them invisible and keeping a reference to them using e.g. `def` or `let` or passing them about as function arguments.
   
  Or you could combine this other actions so you would have a turtle that that would keep track of a game score, and write it on-screen using combinations of `write` and `undo'.
     
  You can set and get an individual property (\"prop\"), or view them all, using the functions:
   [`set-prop`](var:set-prop), [`get-prop`](var:get-prop), [`swap-prop`](var:swap-prop), and [`get-props`](var:get-props).
   
  `swap-prop` allows you to apply a function to the existing property to return a new property.
       
*Examples:*
```
(set-prop :step 1) ;; sets the :step value on a specific turtle to 1
(get-prop :step)   ;; returns the value you set, i.e. 1
(swap-prop :step (fn [v] (+ 2 v)))  ;; the passed-in value is 1, so the new value of ':step' will now be 3
(get-props)  ;; returns a map with all properties: {:step 3}
```  ")


(defn- hex->color [hex & [doubles?]]
  (apply format (if doubles? "[%.2f&nbsp;&nbsp;%.2f&nbsp;&nbsp;%.2f]" "[%s&nbsp;%s&nbsp;%s]")
    (mapv #(let [n (Integer/parseInt (apply str %) 16)] 
             (if doubles? (/ n 255.) n)) 
          (partition 2 (subs hex 1)))))
        

(defn color-palette []
  (let [
        pairs 
        (map #(vec (cs/split (cs/trim %) #" "))  
              (cs/split-lines (slurp (cio/resource "styles/webcolors.txt"))))
        
        sorted-pairs 
        (sort-by second pairs) 
        
        rows 
        (mapv (fn [[n v]] (format  "| <div style=\"background-color:%s; width:100px; height:33px;\" ></div> | :%s | :%s | %s | %s |"  v v n (hex->color v) (hex->color v true)))
              sorted-pairs)
        
        table 
        (format "|  | &nbsp;:hex | &nbsp;:name | &nbsp;RGB |&nbsp;RGB |\n| :--- | :--- | :--- | :--- | :--- | :--- |\n%s\n" (cs/join "\n" rows))]
        
    (str "# Color palette\n140 CSS colors \n***\n" table "\n***\n[source](https://upload.wikimedia.org/wikipedia/commons/2/2b/SVG_Recognized_color_keyword_names.svg)")))


(def topics
  {:Welcome              topic-welcome
   :Color                topic-color
   :color-palette        (color-palette)
   :Clojure              topic-clojure
   :Turtles              topic-turtles
   :Properties           topic-properties})
   ;(keyword (str *ns*))  ((meta *ns*) :doc)}) ;(meta (find-ns (symbol (str *ns*))))


(def headings
  {"Welcome"
   (:Welcome topics)   
   "Turtle (basic)"
   "# Turtle\n\nBasic commands for controlling the turtle."
   "Pen (basic)"
   "# Pen\n\nBasic commands for controlling the turtle's pen."
   "Screen (basic)"
   "# Screen\n\nBasic commands for controlling the screen."

   "Turtle (more)"
   "# Turtle\n\nMore commands for controlling the turtle."
   "Pen (more)"
   "# Pen\n\nMore commands for controlling the turtle's pen."
   "Screen (more)"
   "# Screen\n\nMore commands for controlling the screen."

   "Turtle (advanced)"
   "# Turtle\n\nA collection of advanced commands for controlling turtles."
   
   "Utilities"
   "# Utilities \n\nCustom utility Clojure commands in the turtle API.\n\nSee topic [Clojure](:Clojure) for more information.\n"

   "Samples"
   "# Samples \n\nFun or interesting demonstrations of George's Turtle Geometry."
   
   "Other"
   "# Other\n\nCertain underlying functions that might me interesting to know about or use."

   "clojure.core"
   "# clojure.core\n\nA few of the most used basic Clojure functions. See [Clojure](:Clojure) for more information."})


(defrecord Labeled [label value])

(defn- labeled? [inst]
  (instance? Labeled inst))  


(def turtle-API-list
  ["Welcome"
   ;"Topics"
   :Color
   (->Labeled "Color palette" :color-palette)
   :Turtles
   :Properties
   :Clojure
   "Turtle (basic)"
   #'forward
   #'backward
   #'left
   #'right
   #'show
   #'hide
   #'home
   "Pen (basic)"
   #'pen-up
   #'pen-down
   #'write
   "Screen (basic)"
   #'clear
   #'reset
   "Turtle (more)"
   #'set-speed
   #'get-speed
   #'arc-left
   #'arc-right
   #'set-visible
   #'is-visible
   "Pen (more)"
   #'set-color
   #'get-color
   #'set-width
   #'get-width
   #'set-round
   #'is-round
   #'set-down
   #'is-down
   #'set-fill
   #'get-fill
   #'filled
   #'set-font
   #'get-font
   "Screen (more)"
   #'screen
   #'set-background
   #'get-background
   #'set-axis-visible
   #'is-axis-visible
   #'set-border-visible
   #'is-border-visible
   "Turtle (advanced)"
   #'set-heading
   #'get-heading
   #'set-position
   #'get-position
   #'set-undo
   #'get-undo
   #'undo
   #'set-name
   #'get-name
   #'turtle
   #'with-turtle
   #'filled-with-turtle
   #'new-turtle
   #'clone-turtle
   #'delete-turtle
   #'get-all-turtles
   #'delete-all-turtles
   #'get-state
   #'set-prop
   #'get-prop
   #'get-props
   #'swap-prop
   #'move-to
   #'turn-to
   "Utilities"
   #'rep
   #'sleep
   #'distance-to
   #'heading-to
   "Samples"
   (->Labeled "samples/multi-tree" #'samples/multi-tree)
   "Other"
   #'to-color
   #'to-font])
   
   ;"clojure.core"
   ;#'defn
   ;#'when
   ;#'if
   ;#'let
   ;#'def
   ;#'future])
   
(declare
  rendered-kw-detail
  rendered-var-detail
  detail-webview)


(defn click-handler [detail-fn]
  (fn [kw-or-sym]
    (let [node
          (cond
            (keyword? kw-or-sym) (rendered-kw-detail
                                   kw-or-sym
                                   detail-fn)
            (symbol? kw-or-sym) (rendered-var-detail
                                   (ns-resolve (find-ns 'george.turtle)
                                               kw-or-sym)
                                   detail-fn)
            :default (fx/text (format "ERROR: unknown click-type %s %s" (type kw-or-sym) kw-or-sym)))]

      (detail-fn node))))


(defn rendered-detail
  "Given markdown and a detail-fn, returns a WebView with a kw-handler attached"
  [markdown detail-fn]
  (styled/new-webview (layout/doc->html markdown)
                      (click-handler detail-fn)))


(defn- var->aritylisting [var]
  (let [m (if (labeled? var) (-> var :value meta) (meta var))
        n (if (labeled? var) (:label var) (str (:name m)))
        argls (:arglists m)
        arrity0f "(%s)"
        arrityXf "(%s %s)"]
    (cs/join "\n"
             (map #(if (empty? %)
                     (format arrity0f n)
                     (format arrityXf n (cs/join " " %)))
                  argls))))


(defn ^String var->doc [var]
  (-> var meta :doc))


(defn ^String var->name [vr]
  (-> vr meta :name str))


(defn rendered-var-detail
  "Given a var and a detail-fn, returns a detail-webview."
  [vr detail-fn]
  (let [n (if (labeled? vr) (:label vr) (var->name vr))
        a (var->aritylisting vr)
        d (var->doc (if (labeled? vr) (:value vr) vr))
                            
        md (format "# %s  \n```\n%s\n```  \n***\n\n%s" n a d)]
    (rendered-detail md detail-fn)))


(defn rendered-kw-detail
  "Given a keyword and a detail-fn, returns a detail-webview."
  [kw detail-fn]
  (if-let [md (topics kw)]
    (rendered-detail md detail-fn)
    (rendered-detail "Nothing found for this topic." nil)))


(defn rendered-heading-detail
  "Given a keyword and a detail-fn, returns a detail-webview."
  [heading detail-fn]
  (if-let [md (headings heading)]
    (rendered-detail md detail-fn)
    (rendered-detail "Nothing found for this heading." nil)))


(defn var-label [vr detail-fn]
  (fx/new-label (str "  " (if (labeled? vr) (:label vr) (var->name vr)))
                :tooltip (var->aritylisting (if (labeled? vr) (:value vr) vr))
                :font (fx/new-font "Source Code Pro" 16)
                :mouseclicked #(detail-fn (rendered-var-detail vr detail-fn))))


(defn kw-label [kw detail-fn]
  (fx/new-label (str "     " (if (labeled? kw) (:label kw) (name kw)))
                :font (fx/new-font "Open Sans" :normal 16)
                :color Color/CORNFLOWERBLUE
                :tooltip (format "%s" (if (labeled? kw) (:label kw) (name kw)))
                :mouseclicked #(detail-fn (rendered-kw-detail (if (labeled? kw) (:value kw) kw) 
                                                              detail-fn))))


(defn heading-label [^String heading detail-fn]
  (fx/new-label heading
                :size 18
                :color Color/DARKGREY
                :mouseclicked #(detail-fn (rendered-heading-detail heading detail-fn))))


(defn turtle-API-root []
  (let [[root m-set d-set](layout/master-detail)
        commands turtle-API-list
        labels (map (fn [vr]
                      (cond
                        (var? vr) (var-label vr d-set)
                        (keyword? vr) (kw-label vr d-set)
                        (string? vr) (heading-label vr d-set)

                        (labeled? vr) 
                        (if (keyword? (:value vr)) (kw-label vr d-set) (var-label vr d-set))
                          
                        :default (fx/new-label (str "UNKOWN: " vr))))
                    commands)]
    (m-set
      (doto
        ^ScrollPane
        (fx/scrollpane
          (doto ^VBox (apply fx/vbox (concat (fxj/vargs-t* Node labels) [:padding 10 :spacing 2]))
            (.setFocusTraversable false)))
        (.setFocusTraversable false)
        (.setMinWidth 180)))

    (d-set (rendered-kw-detail :Welcome d-set))
    root))


(defn- create-turtle-API-stage []
  (let [
        stage-WH [600 400]
        screen-WH (-> (fx/primary-screen) .getVisualBounds fx/WH)
        location
        [(- ^int (first screen-WH) ^int (first stage-WH) 10)
         100]]

    (fx/now
      (doto
        (fx/stage
          :style :utility
          :title "Turtle API"
          :alwaysontop true
          :location location
          :size stage-WH
          :resizable true
          :sizetoscene false
          :onhidden #(singleton/remove ::API-stage)
          :scene (fx/scene (turtle-API-root)))
        (styled/style-stage)))))


(defn turtle-API-stage []
  (if-let [st ^Stage (singleton/get ::API-stage)]
    (if (.isAlwaysOnTop st)
      (doto st (.setAlwaysOnTop false) (.toBack))
      (doto st (.setAlwaysOnTop true))))

  (singleton/get-or-create ::API-stage #(create-turtle-API-stage)))


;;;;; 


;(when (env :repl?) (fx/later (turtle-API-stage)))