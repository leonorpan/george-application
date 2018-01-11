;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  george.application.environment
  (:require
    [clojure.string :as cs]

    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.util.singleton :as singleton]

    [george.turtle :as tr]

    [george.application
     [output-input :as oi]
     [editor :as editor]
     [launcher :as launcher]]

    [george.application.ui
       [layout :as layout]
       [styled :as styled]])

  (:import
    [javafx.scene Node]
    [javafx.scene.control SplitPane]
    [javafx.stage Stage]))


;(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(def ide-types #{:ide :turtle})


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
            (symbol? kw-or-sym)  (rendered-var-detail 
                                   (ns-resolve (find-ns 'george.turtle) 
                                               kw-or-sym) 
                                   detail-fn)
            :default  (fx/text (format "ERROR: unknown click-type %s %s" (type kw-or-sym) kw-or-sym)))]            
    
      (detail-fn node)))) 


(defn rendered-detail 
  "Given markdown and a detail-fn, returns a WebView with a kw-handler attached"
  [markdown detail-fn]
  (styled/new-webview (layout/doc->html markdown)
                      (click-handler detail-fn)))


(defn- var->aritylisting [var]
  (let [m (meta var)
        n (str (:name m))
        argls (:arglists m)
        arrity0f "(%s)"
        arrityXf "(%s %s)"]
    (cs/join "  "
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
  (let [n (var->name vr)
        a (var->aritylisting vr)
        d (var->doc vr)
        md (format "# %s  \n`%s`  \n***\n\n%s" n a d)]
    (rendered-detail md detail-fn)))


(defn rendered-kw-detail
  "Given a keyword and a detail-fn, returns a detail-webview."
  [kw detail-fn]
  (if-let [md (tr/topics kw)]
    (rendered-detail md detail-fn)
    (rendered-detail "Nothing found for this topic." nil)))


(defn rendered-heading-detail
  "Given a keyword and a detail-fn, returns a detail-webview."
  [heading detail-fn]
  (if-let [md (tr/headings heading)]
    (rendered-detail md detail-fn)
    (rendered-detail "Nothing found for this heading." nil)))

(defn var-label [vr detail-fn]
  (fx/new-label (str "  " (var->name vr))
                :tooltip (var->aritylisting vr)
                :font (fx/new-font "Source Code Pro" 16)
                :mouseclicked #(detail-fn (rendered-var-detail vr detail-fn))))


(defn kw-label [kw detail-fn]
  (fx/new-label (str "    " (name kw))
                :size  14
                :tooltip  (format "More about %s" (name kw))
                :mouseclicked #(detail-fn (rendered-kw-detail kw detail-fn))))


(defn heading-label [^String heading detail-fn]
  (fx/new-label heading
                :size 18
                :color fx/GREY
                :mouseclicked #(detail-fn (rendered-heading-detail heading detail-fn))))


(defn turtle-API-root []
  (let [[root m-set d-set](layout/master-detail)
        commands tr/turtle-API-list
        labels (map (fn [vr]
                        (cond 
                          (var? vr)     (var-label vr d-set)
                          (keyword? vr) (kw-label vr d-set)
                          (string? vr)  (heading-label vr d-set)
                          :default      (fx/new-label (str "UNKOWN: " vr))))
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


(defn- new-turtle-API-stage []
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


(defn- turtle-API-stage []
  (if-let [st ^Stage (singleton/get ::API-stage)]
    (if (.isAlwaysOnTop st)
      (doto st (.setAlwaysOnTop false) (.toBack))
      (doto st (.setAlwaysOnTop true))))

  (singleton/get-or-create ::API-stage #(new-turtle-API-stage)))


(defn- toolbar-pane [turtle?]
  (fx/hbox
    (styled/new-heading "Turtle Geometry IDE" :size 20)
    (fx/region :hgrow :always)
    (styled/new-link "Turtle API" #(turtle-API-stage))
    :spacing 10
    :padding 10))


(def xy [(+ ^int (launcher/xyxy 2) 5) (launcher/xyxy 1)])


(defn- create-toolbar-stage [ide-type]
  (let [is-turtle (= ide-type :turtle)]
    (fx/now
      (fx/stage
        :location xy
        :title (if is-turtle "Turtle Geometry" "IDE")
        :scene (fx/scene (toolbar-pane is-turtle))
        :sizetoscene true
        :resizable false
        :onhidden #(singleton/remove [::toolbar-stage ide-type])))))



(defn toolbar-stage [ide-type]
  (doto ^Stage
    (singleton/get-or-create [::toolbar-stage ide-type]
                             #(create-toolbar-stage ide-type))
    (.toFront)))



(defn- main-root
  "A horizontal split-view dividing the main area in two."
  [ide-type]
  (let [
        user-ns-str
        (if (= ide-type :turtle) "user.turtle" "user")

        left
        (doto
          (editor/new-tabbed-editor-root :ns user-ns-str :with-one? true))

        oi-root ^SplitPane
        (doto
          (oi/output-input-root :ns user-ns-str)
          (.setStyle "-fx-box-border: transparent;"))

        root
        (doto
          (SplitPane. (fxj/vargs-t Node left oi-root))
          (.setDividerPosition 0 0.5)
          (.setStyle "-fx-box-border: transparent;"))]

    ;; TODO: Implement general ratio function for height of total height
    ;; TODO: Calculate height based on application stage or passed-in dim.
    (.setDividerPosition oi-root 0 0.7)
    ;; TODO: Ensure input gets focus!
    ;; TODO: Nicer SplitPane dividers!  See
    root))


(defn- ide-root-create [ide-type]
  (assert (ide-types ide-type))
  (let [[root master-setter detail-setter] (layout/master-detail true)]
    (master-setter (doto (toolbar-pane ide-type)
                         (.setBorder (styled/new-border [0 0 1 0]))))
    (detail-setter (main-root ide-type))
    (doto root (.setBorder (styled/new-border [0 0 0 1])))))


(defn ide-root [ide-type]
  (singleton/get-or-create [::ide-root ide-type]
                           #(ide-root-create ide-type)))


(defn ide-root-dispose [ide-type]
  (assert (ide-types ide-type))
  (singleton/remove [::ide-root ide-type]))
