(ns
  george.code.codearea
  (:require
    [george.javafx :as fx] :reload


            )
    (:import [org.fxmisc.richtext StyledTextArea LineNumberFactory]
             [java.util.function BiConsumer IntFunction Function]
             [java.util Collections]
             [javafx.scene.text Text]
             [javafx.scene.paint Color]
             [org.reactfx.value Val]
             [javafx.geometry Pos]))


(defrecord
  ^{:doc "The data type used for styling code area"}
  StyleSpec [color weight underline background])


(def
  ^{:doc "Default StyleSpec for codearea.  Used for spans without explicit style, including:
     white-space, comments, et al."}
  DEFAULT_SPEC (->StyleSpec "GRAY" "bold" "false" "null"))


(declare type->color)


(defn- ^String style
  "returns JavaFX-css based on StyleSpec"
  [^StyleSpec spec]
  (let [{c :color w :weight u :underline b :background} spec]
    (str
      "-fx-fill: " (if c c (type->color :default)) "; "
      "-fx-font-weight: " (if w w "normal") "; "
      "-fx-underline: " (if u u "false") "; "
      "-fx-background-fill: " (if b b "null") "; "
      ;"-fx-effect: " (if h "dropshadow(one-pass-box, slategray, 5, 1, 0, 0)" "null") "; "
      )))


(defn- apply-specs
  "called by style-byconsumer"
  [^Text text specs]
  (if (instance? StyleSpec specs)
    (. text setStyle (style specs))
    (if (= Collections/EMPTY_LIST specs)
      (. text setStyle (style DEFAULT_SPEC))
      (doseq [spec specs]
        (. text setStyle (style spec)))))
  ;(.setCursor (if h Cursor/DEFAULT nil))
  )



(defn- style-biconsumer
  "passed to codearea on instanciation"
  []
  (reify BiConsumer
    (accept [_ text style]
      (apply-specs text style))))




(defn- ->arrowfactory [showline]  ;; ObservableValue<Integer>
    (reify IntFunction
        (apply [_ linenumber]
            (let [
                  triangle
                  (doto
                      (fx/polygon
                          0.  0.
                          2.  3.
                          0.  6.)
                      (-> .getStyleClass (. add "linetriangle")))


                  visible
                  (Val/map
                      showline
                      (reify Function
                          (apply [_ sl]
                              (= sl linenumber))))

                  ]

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
        numberfactory
                     (LineNumberFactory/get codearea)
        arrowfactory
                     (->arrowfactory (. codearea currentParagraphProperty))
        graphicfactory
                     (reify IntFunction
                         (apply [_ line]
                             (fx/hbox
                                 (. numberfactory apply line)
                                 (. arrowfactory apply line)
                                 :alignment Pos/CENTER_LEFT)))
        ]
    (doto codearea
      (. setParagraphGraphicFactory  graphicfactory))))



(defn ^StyledTextArea ->codearea []
    (doto
      (StyledTextArea. DEFAULT_SPEC (style-biconsumer))
    (. setFont (fx/SourceCodePro "Medium" 18))
    (. setStyle "
            -fx-padding: 0;
            -fx-background-color: WHITESMOKE;")
    (. setUseInitialStyleForInsertion true)
    (-> .getUndoManager .forgetHistory)
    (-> .getUndoManager .mark)
    (. selectRange 0 0)))


(defn text [^StyledTextArea codearea]
    (. codearea getText 0 (. codearea getLength)))


(defn set-text [^StyledTextArea codearea text]
    (. codearea replaceText text))
