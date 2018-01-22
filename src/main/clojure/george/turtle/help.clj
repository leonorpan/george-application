;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle.help
  (:require
    [clojure.string :as cs]
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
    (javafx.scene.paint Color)))

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

")


(def topics
  {:Welcome              topic-welcome
   :Color                topic-color
   :Clojure              topic-clojure
   :Turtles              topic-turtles})
   ;(keyword (str *ns*))  ((meta *ns*) :doc)}) ;(meta (find-ns (symbol (str *ns*))))


(def headings
  {"Welcome"
   (:Welcome topics)   
   "Turtle"
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
   "Special"
   "# Special\n\nCertain functions that might me interesting to know about."
   "Topics"
   "# Topics\n\nIn-depth on certain topics of interest."
   "clojure.core"
   "# clojure.core\n\nA few of the most used basic Clojure functions. See [Clojure](:Clojure) for more information."})


(defrecord Labeled [label value])

(defn- labeled? [inst]
  (instance? Labeled inst))  


(def turtle-API-list
  ["Welcome"
   ;"Topics"
   :Color
   :Turtles
   :Clojure
   "Turtle"
   #'tr/forward
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
   #'write
   #'filled
   "Pen"
   #'pen-up
   #'pen-down
   #'set-down
   #'is-down
   #'set-color
   #'get-color
   #'set-fill
   #'get-fill
   #'set-width
   #'get-width
   #'set-round
   #'is-round
   #'set-font
   #'get-font
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
   #'with-turtle
   #'filled-with-turtle
   #'new-turtle
   #'clone-turtle
   #'delete-turtle
   #'get-all-turtles
   #'delete-all-turtles
   #'set-prop
   #'get-prop
   #'get-props
   #'swap-prop
   "Samples"
   (->Labeled "samples/multi-tree" #'samples/multi-tree)
   "Special"
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
  (fx/new-label (str "     " (name kw))
                :font (fx/new-font "Open Sans" :normal 16)
                :color Color/CORNFLOWERBLUE
                :tooltip (format "%s" (name kw))
                :mouseclicked #(detail-fn (rendered-kw-detail kw detail-fn))))


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
                        (labeled? vr) (var-label vr d-set)
                        :default (fx/new-label (str "UNKOWN: " vr))))
                    commands)]
    (m-set
      (doto
        (fx/scrollpane
          (doto (apply fx/vbox (concat (fxj/vargs-t* Node labels) [:padding 10 :spacing 2]))
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


(when (env :repl?) (fx/later (turtle-API-stage)))