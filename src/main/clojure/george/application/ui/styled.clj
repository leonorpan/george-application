;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.ui.styled
  (:require
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [clojure.java.io :as cio])
  (:import
    [javafx.scene.paint Color]
    [javafx.scene Scene]
    [javafx.stage Stage]
    [javafx.scene.image Image]))



(defn heading [s & {:keys [size] :or {size 16}}]
  (fx/text s  :size size :color fx/GREY))


(defn padding [h]
  (fx/line :x2 h :y2 h :color Color/TRANSPARENT))


(defn hr [w]
  (fx/line :x2 w :width 1 :color Color/GAINSBORO))

(defn new-border [w]
  (fx/new-border Color/GAINSBORO w))


(defn skin-scene [^Scene scene]
  (fx/set-Modena)  ;; This should clear the StyleManager's "cache" so everything is reloaded.
  (doto scene
    (->  .getStylesheets .clear)
    (fx/add-stylesheets "styles/basic.css")))


(defn add-icon [^Stage stage]
  (fx/later
     (-> stage
       .getIcons
       (.setAll
         (fxj/vargs*
           (map #(Image. (format "graphics/George_icon_%s.png" %))
                 [16 32 64 128 256])))))
  stage)


(defn style-stage [^Stage stage]
  (doto stage
        add-icon
        (-> .getScene skin-scene)))

