;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  george.code.codearea
  (:require
    [george.javafx :as fx])
  (:import [org.fxmisc.richtext StyledTextArea LineNumberFactory]
           [java.util.function BiConsumer IntFunction Function]
           [java.util Collections]
           [javafx.scene.text Text]
           [javafx.scene.paint Color]
           [org.reactfx.value Val]
           [javafx.geometry Pos]
           [javafx.beans.property SimpleObjectProperty]))


(defrecord
  ^{:doc "The data type used for styling code area"}
  StyleSpec [color weight underline background])


(def
  ^{:doc "Default StyleSpec for codearea.  Used for spans without explicit style, including:
     white-space, comments, et al."}
  DEFAULT_SPEC (->StyleSpec "GRAY" "bold" "false" "null"))



(defn- ^String style
  "returns JavaFX-css based on StyleSpec"
  [^StyleSpec spec]
  (let [{c :color w :weight u :underline b :background} spec]
    (str
      "-fx-fill: " (if c c "#404042") "; "
      "-fx-font-weight: " (if w w "normal") "; "
      "-fx-underline: " (if u u "false") "; "
      "-fx-background-fill: " (if b b "null") "; ")))
      ;"-fx-effect: " (if h "dropshadow(one-pass-box, slategray, 5, 1, 0, 0)" "null") "; "



(defn- apply-specs
  "called by style-byconsumer"
  [^Text text specs]
  (if (instance? StyleSpec specs)
    (. text setStyle (style specs))
    (if (= Collections/EMPTY_LIST specs)
      (. text setStyle (style DEFAULT_SPEC))
      (doseq [spec specs]
        (. text setStyle (style spec))))))
  ;(.setCursor (if h Cursor/DEFAULT nil))




(defn- style-biconsumer
  "passed to codearea on instanciation"
  []
  (reify BiConsumer
    (accept [_ text style]
      (apply-specs text style))))




(defn- ->errormarkfactory [errorlineset]  ;; ObservableValue<Object>
    (reify IntFunction
        (apply [_ linenumber]  ;; ObservableValue<Integer>
            (let [

                  mark
                  (doto
                      ;; upward triangle
                      (fx/polygon   5. 0.   10. 10.   0. 10.)
                      (-> .getStyleClass (. add "line-error-mark")))


                  show-mark
                  (Val/map errorlineset
                           (reify Function
                               (apply [_ errorlineset]
                                   ;; linenumber is 0-based, while errorlinest is 1-based
                                   (boolean (get errorlineset (inc linenumber))))))]



                (-> mark .visibleProperty
                    (. bind
                       (Val/flatMap
                           (. mark sceneProperty)
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
                            (-> .getStyleClass (. add "line-error-background")))

                  show-background
                        (Val/map errorlineset
                                 (reify Function
                                     (apply [_ errorlineset]
                                         (not (empty? errorlineset)))))]


                (-> background .visibleProperty
                    (. bind
                       (Val/flatMap
                           (. background sceneProperty)
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
                      (-> .getStyleClass (. add "linetriangle")))

                  visible
                  (Val/map
                      current-line
                      (reify Function
                          (apply [_ cl]
                              (= cl linenumber))))]


                (-> triangle .visibleProperty
                    (. bind
                       (Val/flatMap
                           (. triangle sceneProperty)
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
                     (->errorbackgroundfactory (. codearea errorlines))
        errormarkfactory
                     (->errormarkfactory (. codearea errorlines))
        arrowfactory
                     (->arrowfactory (. codearea currentParagraphProperty))
        graphicfactory
                     (reify IntFunction
                         (apply [_ line]
                             (fx/hbox
                                 (. linenumberfactory apply line)
                                 (. errorbackgroundfactory apply line)
                                 (. errormarkfactory apply line)
                                 (. arrowfactory apply line)
                                 :alignment Pos/CENTER_LEFT)))]

    (doto codearea
      (. setParagraphGraphicFactory  graphicfactory))))



(definterface IErrorLines
    (errorlines ^SimpleObjectProperty []))


(defn ^StyledTextArea ->codearea []
    (let [lines (SimpleObjectProperty. #{})]

        (doto
            (proxy [StyledTextArea IErrorLines] [DEFAULT_SPEC (style-biconsumer)]
                (errorlines [] lines))
            (. setFont (fx/SourceCodePro "Medium" 16))
            (. setStyle "
            -fx-padding: 0;
            -fx-background-color: WHITESMOKE;")
            (. setUseInitialStyleForInsertion true)
            (-> .getUndoManager .forgetHistory)
            (-> .getUndoManager .mark)
            (. selectRange 0 0))))


(defn text [^StyledTextArea codearea]
    (. codearea getText 0 (. codearea getLength)))


(defn set-text [^StyledTextArea codearea text]
    (. codearea replaceText text))
