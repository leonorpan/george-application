;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.app-loader
  (:require [clojure.tools.namespace.find :refer [find-sources-in-dir find-namespaces]]
            [clojure.java.io :as cio]
            [clojure.string :as cs]
            [clojure.pprint :refer [pprint]]
            [clojure.java.classpath :as cp]
            [george.javafx :as fx])
  (:import (javafx.scene.paint Color)
           (javafx.scene.control Button)
           (javafx.scene.text TextAlignment)
           (javafx.geometry Pos)
           (javafx.scene.image ImageView Image)))


(defn- verify-apps
  [app-ns]
  (println "Verifying apps:" app-ns)
;(try
  (require app-ns)
  (if-let [info-fn (ns-resolve app-ns 'app-info)]
    (try
      (let [info (info-fn)]
        (if-let [name (:george.application.app/name info)]
          (if-let [description (:george.application.app/description info)]
            (if-let [icon-fn (ns-resolve app-ns (:george.application.app/icon-fn info))]
              (if-let [main-fn (ns-resolve app-ns (:george.application.app/main-fn info))]
                (do
                  ;(println "  ## main:" main (type main))
                  ;(println "  ## main-fn:" main-fn (type main-fn))
                  {:ns app-ns
                   :name name
                   :description description
                   :icon-fn icon-fn
                   :main-fn main-fn})

                (println "  ERROR: The applet info has no :george.application.app/main-fn"))
              (println "  ERROR: The applet info has no :george.application.app/icon-fn"))
            (println "  ERROR: The applet info has no :george.application.app/description"))
          (println "  ERROR: The applet info has no :george.application.app/name")))
      (catch Exception e (println (format "  ERROR: Calling %s/info failed!  %s" app-ns  e)) (set! *e e)))
    (println "  ERROR: The app's 'info' function could not be resolved!")))
  ;(catch Exception e (println "  ERROR: Loading applet namespace failed!") (.printStackTrace e))))


(defn find-apps
  "returns lazy seq of all namespaces matching 'george.application.applet.xyz'"
  []
  (filter
    #(re-find #"george.application.app\..+" (str %))
    (find-namespaces (cp/classpath))))


(defn load-apps []
  (let [apps-ns-list (vec (find-apps))
        verified-info-list (vec (map verify-apps apps-ns-list))]
    (filter some? verified-info-list)))




;; DEV
;(println (str "  ## find-apps: " (vec (find-apps))))
;(doseq [a (load-apps)] (println (str "  -a: " a)))
;(println (str "  ## applets:\n"  (cs/join "\n" (load-applets))))





(defn launcher-app-tile
  "Builds a 'tile' (a parent) containing a labeled button (for the launcher)."
  [app-info width]
  (let [
        w (- width 8)
        iw (- w 10)
        tile
        (fx/vbox
          (doto (Button. nil
                         ((:icon-fn app-info) iw iw))
            (.setOnAction (fx/event-handler ((:main-fn app-info))))
            (fx/set-tooltip (:description app-info))
            (.setPrefWidth w)
            (.setPrefHeight w)
            (.setStyle "-fx-background-radius: 8;"))
          (doto (fx/label (:name app-info))
            (.setStyle "-fx-font-size: 12px;")
            (.setMaxWidth w)
            (.setWrapText true)
            (.setTextAlignment TextAlignment/CENTER))
          :alignment Pos/CENTER
          :spacing 5)]
    tile))



(defn padding [h]
  (fx/line :x2 h :y2 h :color Color/TRANSPARENT))

(defn hr [w]
  (fx/line :x2 w :width 1 :color Color/GAINSBORO))


(defn- launcher-test []
  (fx/later
    (let [
          tile-w 64

          george-icon
          (doto (ImageView. (Image. "graphics/George_icon_128_round.png"))
            (.setFitWidth tile-w)
            (.setFitHeight tile-w))


          app-infos
          (load-apps)

          app-tiles-and-paddings
          (flatten
            (map #(vector
                    (launcher-app-tile % (- tile-w 8))
                    (padding 30))
                 app-infos))

          root
          (apply fx/vbox
            (concat
              [
               (padding 10)
               george-icon
               (padding 10)
               (hr (+ tile-w 20))
               (padding 30)]

              app-tiles-and-paddings
              [
               (hr (+ tile-w 20))
               (padding 5)
               (doto (fx/label "About") (.setStyle "-fx-font-size: 10px;"))
               (padding 5)
               :alignment Pos/TOP_CENTER]))

          scene
          (fx/scene root)]

      (fx/stage :scene scene :title "George"))))

;(launcher-test)