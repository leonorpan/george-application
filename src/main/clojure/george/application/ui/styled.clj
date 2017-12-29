;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.ui.styled
  (:require [george.javafx :as fx])
  (:import (javafx.scene.paint Color)))


(defn heading [s & {:keys [size] :or {size 16}}]
  (fx/text s  :size size :color fx/GREY))


(defn padding [h]
  (fx/line :x2 h :y2 h :color Color/TRANSPARENT))


(defn hr [w]
  (fx/line :x2 w :width 1 :color Color/GAINSBORO))
