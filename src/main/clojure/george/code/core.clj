;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  george.code.core
  (:require
     [george.code.highlight :as highlight]
     [george.code.codearea :as ca]
     [george.code.paredit :as paredit]
     [george.javafx :as fx])
  (:import [org.fxmisc.richtext StyledTextArea]
           (javafx.scene.input KeyEvent)
           (javafx.scene.text Font)))



(defn- font-size-handler []
  (fx/event-handler-2
    [this e]
    (let [c (.getCharacter e)
          shortcut? (.isShortcutDown e)]
      (when (and (#{"+" "-"} c) shortcut?)
        (let [source (.getSource e)
              font (.getFont source)
              size (.getSize font)
              new-size (+ size (if (= "+" c) 2 -2))
              new-font (Font. (.getName font) new-size)]
          (.setFont source new-font))))))  ;; TODO: No change!  :-(
          ;(println "  ## source:" source)
          ;(println "  ## font:" font)
          ;(println "  ## size:" size)
          ;(println "  ## new-size:" new-size))))))


(defn ^StyledTextArea ->codearea []
    (doto
        (ca/->codearea)
      (ca/set-linenumbers)
      (paredit/set-handlers)
      (highlight/set-handlers)))
      ;(.addEventHandler KeyEvent/KEY_TYPED (font-size-handler))))


(defn text [codearea]
    (ca/text codearea))


(defn set-text [codearea text]
    (ca/set-text codearea text))


(defn -main [& _]
  (fx/later
    (let [
          ca
          (doto
              (->codearea)
              (set-text "(foo (bar 1))"))

          scene
          (doto
              (fx/scene (fx/borderpane :center ca :insets 1))
              (fx/add-stylesheets  "styles/codearea.css"))]

         (fx/stage
             :title "george.code.core/-main (test)"
             :scene scene
             :size [600 400]))))






;;; DEV ;;;

;(println "WARNING: Running george.code.core/-main" (-main))