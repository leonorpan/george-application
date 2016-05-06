(ns
    ^{:author "Terje Dahl"}
    george.core.core
    (:require
        [clojure.repl :as cr]
        [clojure.pprint :refer [pprint pp] :as cpp]
        [george.javafx :as fx]
        :reload)

    (:import (javafx.geometry Pos)
             (javafx.scene.paint Color)
             (javafx.scene.control Tooltip ListCell)
             (javafx.util Callback)
             (clojure.lang Var)))



(defrecord NsThing [k v t]
    Object
    (toString [_]
        (let [derefed (deref v)]

         (format "%s   %s  %s   %s" t k (if (fn? derefed) "FN" "") (meta v)))))


(defn print-all [nsthing]
    (let [
          {:keys [k v t]} nsthing
          m (meta v)
          n (:name m)
          doc (:doc m)

          src (cr/source-fn (symbol (str (:ns m))  (str (:name m))))]

        (printf "\n  ## %s ##\n" (name t))
        (println  "  name: " n)
;        (println  "  meta: " (cpp/write (dissoc met :ns) :stream nil))
        (println  "  meta: " (dissoc m :ns))
        (println  "   doc: " doc)
        (println  "   src: " src)))


(defn- tooltip [nsthing]
    (let [
          {:keys [k v t]} nsthing
          derefed (deref v)
          s (format "%s: \n%s   ->  %s \nfn? %s \n%s"
                    (name t)
                    k v
                    (fn? derefed)
                    ;(meta v)
                    (cpp/write (dissoc (meta v) :ns) :stream nil))]


        (doto (Tooltip. s)
            (. setFont (fx/SourceCodePro "Medium" 16))
            (. setStyle "
            -fx-padding: 5;
            -fx-background-radius: 2 2 2 2;
            -fx-text-fill: #2b292e;
            -fx-background-color: WHITESMOKE;"))))


(defn- a-namespace-listcell-factory []
    (let[]
;         lbl (fx/label)
 ;        tt (Tooltip.)

     (reify Callback
         (call [this view]
             (proxy [ListCell] []
                 (updateItem [item empty?]
                     (proxy-super updateItem item empty?)
                     (when-not empty?
                        ;(.setText  lbl (str item))
                         (.setText this (str item))
;                        (.setText tt (str "TT: " item))
                         (.setTooltip this  (tooltip item)))

                     this))))))


(defn- a-namespace-scene [namespace]
    (let [
          interns (map (fn [[k v]] (->NsThing k v :interned)) (ns-interns namespace))
          refers (map (fn [[k v]] (->NsThing k v :refered)) (ns-refers namespace))
          aliases (map (fn [[k v]] (->NsThing k v :aliased)) (ns-aliases namespace))
          imports (map (fn [[k v]] (->NsThing k v :imported)) (ns-imports namespace))

          all (concat
                  interns
                  refers
                  aliases
                  imports)


          view (fx/listview
                   (apply fx/observablearraylist-t Object
                          all))]



        (-> view
            .getSelectionModel
            .selectedItemProperty
            (.addListener
                (fx/changelistener [_ _ _ val]
                                   (print-all val))))

        (. view (setCellFactory (a-namespace-listcell-factory)))
        (fx/scene view)))



(defn- a-namespace-stage [namespace]
    (let [bounds (. (fx/primary-screen) getVisualBounds)]

        (fx/now
            (fx/stage
                :style :utility
                :title (str "namespace: " namespace)
                :location [(-> bounds .getMinX (+ 350))(-> bounds .getMinY (+ 120))]
                :size [350 350]
                :scene (a-namespace-scene namespace)))))



;;;; namespaces section ;;;;

(defn- namespaces []
    (sort #(compare (str %1) (str %2))
          (all-ns)))


(defn- namespaces-scene []
    (let [
          view (fx/listview (apply fx/observablearraylist (namespaces)))]

        (-> view
            .getSelectionModel
            .selectedItemProperty
            (.addListener
                (fx/changelistener [_ _ _ val]
                                   (println "namespace:" val)
                                   (a-namespace-stage val))))

        (fx/scene view)))


(defn- namespaces-stage []
    (let [bounds (. (fx/primary-screen) getVisualBounds)]

        (fx/now
            (fx/stage
                :style :utility
                :title "Namespaces"
                :location [(. bounds getMinX)(-> bounds .getMinY (+ 120))]
                :size [350 350]
                :scene (namespaces-scene)))))



(defonce ^:private namespaces-singleton (atom nil))

(defn show-or-create-namespaces-stage []
    (if @namespaces-singleton
        (. @namespaces-singleton toFront)
        (reset! namespaces-singleton
                (fx/setoncloserequest (namespaces-stage)
                                      #(do
                                          (println "reset!-ing namespaces-singleton to nil")
                                          (reset! namespaces-singleton nil))))))


;;; launcher section ;;;;


(defn- launcher-scene []
    (let [
          button-width
          150

          namespace-button
          (fx/button
              "Namespaces"
              :width button-width
              :onaction #(show-or-create-namespaces-stage)
              :tooltip "Open/show namespace-list")


          logo
          (fx/imageview "graphics/George_logo.png")]



        (fx/scene
            (doto (fx/hbox
                   logo namespace-button
                   :spacing 20
                   :padding 20
                   :alignment Pos/CENTER_LEFT)
                (. setBackground (fx/color-background Color/WHITE))))))


(defn- launcher-stage []
    (let [bounds (. (fx/primary-screen) getVisualBounds)]

        (fx/now
            (fx/stage
                :style :utility
                :title "George"
                :location [(. bounds getMinX)(. bounds getMinY)]
                :size [(. bounds getWidth) 120]
                :scene (launcher-scene)))))



#_(defn- scene-root []
   (let [
         root (fx/borderpane
                :center (fx/rectangle :fill (fx/web-color "yellow"))
                :insets 0)]



     root))


#_(defn -main [& args]
   (fx/later
     (fx/stage
       :title "George :: core"
       :size [600 600]
       :scene (fx/scene (scene-root)))))




(defonce ^:private launcher-singleton (atom nil))

(defn show-or-create-launcher-stage []
    (if-not @launcher-singleton
        (reset! launcher-singleton
                (fx/setoncloserequest (launcher-stage)
                                      #(do
                                          (println "reset!-ing launcher-singleton to nil")
                                          (reset! launcher-singleton nil))))))



;;; DEV ;;;

;(println "  ## WARNING: running `-main` from george.core.core") (-main)
;(println "  ## WARNING: running `show-or-create-launcher-stage` from george.core.core") (show-or-create-launcher-stage)
