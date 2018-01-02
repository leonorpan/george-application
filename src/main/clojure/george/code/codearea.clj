;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  george.code.codearea
  (:require
    [george.javafx :as fx]
    [george.code.highlight :as highlight]
    [george.util.css :as css]
    [george.util :as u])
  (:import [org.fxmisc.richtext LineNumberFactory CodeArea StyleClassedTextArea]
           [java.util.function IntFunction Function]
           [javafx.scene.paint Color]
           [org.reactfx.value Val]
           [javafx.geometry Pos]
           [javafx.beans.property SimpleObjectProperty]
           (javafx.scene.input KeyEvent)))



(defn- ->errormarkfactory [errorlineset]  ;; ObservableValue<Object>
    (reify IntFunction
        (apply [_ linenumber]  ;; ObservableValue<Integer>
            (let [

                  mark
                  (doto
                      ;; upward triangle
                      (fx/polygon   5. 0.   10. 10.   0. 10.)
                      (-> .getStyleClass (.add "line-error-mark")))

                  show-mark
                  (Val/map errorlineset
                           (reify Function
                               (apply [_ errorlineset]
                                   ;; linenumber is 0-based, while errorlinest is 1-based
                                   (boolean (get errorlineset (inc linenumber))))))]

                (-> mark .visibleProperty
                    (. bind
                       (Val/flatMap
                           (.sceneProperty mark)
                           (reify Function
                               (apply [_ scene]
                                   (if scene show-mark (Val/constant false)))))))

                mark))))


(defn- ->errorbackgroundfactory [errorlineset]  ;; ObservableValue<Object>
    (reify IntFunction
        (apply [_ linenumber] ;; ObservableValue<Integer>
            (let [

                  background
                        (doto
                            ;; colored background (for whole bar, if error)
                            (fx/rectangle
                                :size [18 24]
                                :fill Color/HOTPINK)
                            (-> .getStyleClass (.add "line-error-background")))

                  show-background
                        (Val/map errorlineset
                                 (reify Function
                                     (apply [_ errorlineset]
                                         (not (empty? errorlineset)))))]


                (-> background .visibleProperty
                    (.bind
                       (Val/flatMap
                           (.sceneProperty background)
                           (reify Function
                               (apply [_ scene]
                                   (if scene show-background (Val/constant false)))))))
                background))))



(defn- ->arrowfactory [current-line]  ;; ObservableValue<Integer>
    (reify IntFunction
        (apply [_ linenumber]
            (let [
                  triangle
                  (doto
                      ;; right-pointing shallow triangle
                      (fx/polygon  0. 0.   2. 3.   0. 6.)
                      (-> .getStyleClass (.add "linetriangle")))

                  visible
                  (Val/map
                      current-line
                      (reify Function
                          (apply [_ cl]
                              (= cl linenumber))))]


                (-> triangle .visibleProperty
                    (.bind
                       (Val/flatMap
                           (.sceneProperty triangle)
                           (reify Function
                               (apply [_ scene]
                                   (if scene visible (Val/constant false)))))))
                triangle))))



(defn set-linenumbers [codearea]
  (let [
        ;; http://stackoverflow.com/questions/28659716/show-breakpoint-at-line-number-in-richtextfx-codeare
        linenumberfactory
        (LineNumberFactory/get codearea)
        errorbackgroundfactory
        (->errorbackgroundfactory (.errorlines codearea))
        errormarkfactory
        (->errormarkfactory (.errorlines codearea))
        ;arrowfactory
        ;(->arrowfactory (.currentParagraphProperty codearea))
        graphicfactory
                     (reify IntFunction
                         (apply [_ line]
                             (fx/hbox
                                 (.apply linenumberfactory line)
                                 (.apply errorbackgroundfactory line)
                                 (.apply  errormarkfactory line)
                                 ;(. arrowfactory apply line)
                                 :alignment Pos/CENTER_LEFT)))]

    (doto codearea
      (.setParagraphGraphicFactory graphicfactory))))





(defn- font-size-handler []
  (fx/event-handler-2
    [this e]
    (let [c (.getCharacter e)
          shortcut? (.isShortcutDown e)]
      (when (and (#{"+" "-"} c) shortcut?)
        (let [source (.getSource e)
              style-map (css/stylable->style-map source)
              size (Integer/parseInt (style-map "-fx-font-size"))
              new-size (u/clamp-int 8 (+ size (if (= "+" c) 2 -2)) 72)]
          (css/set-style-map source
                             (assoc style-map "-fx-font-size" new-size)))))))


(definterface IErrorLines
  (errorlines ^SimpleObjectProperty []))


;; Similar to CodeArea, but with some different settings - including my own CSS
(defn new-codearea
 ([] (new-codearea true))
 ([linenumbers?]
  (let [lines
        (SimpleObjectProperty. #{})

        ca
        (proxy [StyleClassedTextArea IErrorLines] [false]
          (errorlines [] lines))

        lnf
        (when linenumbers? (LineNumberFactory/get ca))]
    (doto ca
      (-> .getStylesheets (.add "styles/codearea.css"))
      (-> .getStyleClass (.add "codearea"))
      (.setStyle "-fx-font-size: 16;")
      (.setUseInitialStyleForInsertion true));
    (if linenumbers?
      (.setParagraphGraphicFactory ca lnf)
      ca))))

(defn ^CodeArea new-codearea-with-handlers []
  (doto
    (new-codearea false)
    (set-linenumbers)
    (highlight/set-handlers)
    (.addEventHandler KeyEvent/KEY_TYPED (font-size-handler))))




(defn ^String text [^StyleClassedTextArea codearea]
    (.getText codearea  0 (.getLength codearea)))


(defn set-text [^StyleClassedTextArea codearea ^String text]
    (.replaceText codearea text))




(defn -main [& _]
  (fx/later
    (let [
          ca
          (doto
            (new-codearea-with-handlers)
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