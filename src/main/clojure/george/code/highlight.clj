(ns george.code.highlight
    (:require
        [clojure.repl :refer [doc]]
        [clojure.pprint :refer [pp pprint]]

        [clojure.core.async :refer [go thread chan >! >!! <! <!! go-loop sliding-buffer]]

        [george.java :as j] :reload
        [george.javafx :as fx] :reload

        [george.code.reader :as my] :reload
        [george.code.tokenizer :as tok] :reload
        )
    (:import
        [java.util Collections]
        [clojure.lang Keyword Symbol]
        [org.fxmisc.richtext LineNumberFactory StyledTextArea MouseOverTextEvent StyleSpansBuilder]
        [george.code.tokenizer TokenError Arg DelimChar Comment MacroChar MacroDispatchChar]

        [javafx.scene.text Text]
        [java.util.function BiConsumer]
        [java.time Duration]))


(defn- clamp
    "ensures val does not exceed min or max"
    [min val max]
    (cond
        (< val min) min
        (> val max) max
        :else val))

(declare type->color)

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



(defn get-text [^StyledTextArea codearea]
    (. codearea getText 0 (. codearea getLength)))


(defn set-text [^StyledTextArea codearea text]
    (. codearea replaceText text))



(defn- code-textarea []
    (doto (StyledTextArea.
              DEFAULT_SPEC
              (style-biconsumer))
        (. setFont (fx/SourceCodePro "Medium" 18))
        (. setStyle "
            -fx-padding: 0;
            -fx-background-color: WHITESMOKE;"
        )

        (. setUseInitialStyleForInsertion true)
        (-> .getUndoManager .forgetHistory)
        (-> .getUndoManager .mark)
        (. selectRange 0 0)
    ))

(defn- background-fill [hover?]
    (if hover? "yellow" nil))


(defn- set-style [codearea code-len ranges style hover?]
    (doseq [[from to] ranges]
        (when (and from to)  ;; skip [nil nil] ranges.  Fix!
            (let [
                 ;; ensure bounds
                 new-style  (assoc style :background (background-fill hover?))
                 from (max 0 from)
                 to (min to code-len)
                 ]
;            (println "new-style:" from to new-style)
            (. codearea setStyle  from to new-style)

            ))))



(defn- unpaired-delim? [token]
    ;(println "unpaired?" token)
    (boolean (get-in token [:value :unpaired])))

(defn- mark-as-unpaired-delim [token]
    (assoc-in token [:value :unpaired] true))


(defn- type->color [typ]
        ;(println "color typ:" typ)
        (cond
            ;; cursor color? "#26A9E1"

            (#{Symbol :default}  typ)
            "#404042";"#01256e";"#333"

            (#{Boolean :nil} typ)
            "#4F7BDE";"#524EAC";"#1FAECE";"#00ACEE";"#31B2F4"

            (#{TokenError :unpaired} typ)
            "#ED1C24";"red";"#B3191F";

            (isa? typ Number)
            "#524EAC";"#005d69";"#26A9E1";"#00ACEE";"#6897bb";"#262161";"#4a0042";"#336699"

            (#{Keyword Arg} typ)
            "#9E1F64"

            (#{String Character} typ)
            "#008e00";"#008000"

            (#{DelimChar} typ)
            "#99999c";"#D1D2D4";"lightgray"
            ; "#f2c100" ;; yellow

            (#{Comment}  typ)
            "#708080"

            (#{MacroChar MacroDispatchChar}  typ)
            "#cc7832";"#c35a00";"#A33EFE"

            :else
            "orange"
        ))


(defn- color [token]
    (let [
             value (:value token)
             typ
                (cond
                    (= :nil value)
                    :nil

                    (unpaired-delim? token)
                    :unpaired

                    :else
                    (type value))
         ]
        (type->color typ)))


#_(defn- assoc-token
    "assocs a given token to every index it covers"
    [rngs token token-index]
    (doseq [rng rngs]
        (doseq [i (apply range rng)]
            (swap! token-index assoc i token))))


(defn- token->range
"Takes a token and returns a range-vector:  Token -> [start end]"
    [token]
    [(:start token) (:end token)])



(defn- token->ranges
"Takes a Token or a pair of tokens in a vector and returns a vector of range-vectors:
Token -> [[start end]]
[Token Token] -> [[start end] [start end]]"
    [thing]
    (if (vector? thing)
        [(token->range (first thing)) (token->range (second thing))]
        [(token->range thing)]))



(defn- assoc-token
    [index-vector! rngs token]
    (if-not token  ;; avoid NullPointerExeptions.  Fix this!
        index-vector!
    (let [
             indexes
             (reduce
                 (fn [acc rng] (concat acc (apply range rng)))
                 [] rngs)
        ]
        (loop [indv index-vector!
               inds indexes]
            (if (empty? inds)
                indv
                (recur
                    (assoc! indv (first inds) token)
                    (rest inds))))))
    )




(defn- create-token-index
"assocs a seq of single and paired tokens to every index they cover:
[Token0{:start 0 :end 2 ...}  Token1{:start 4 :end 5 ...} ...] -> [Token0 Token0 Token0 nil Token1 ...]
all-tokens:  seq of single tokens:  [Token0 Token1 Token2 Token3 Token4 Token 5]
paired-tokens: seq of vectors of paired tokens: [[Token0 Token4][Token1 Token3]]
"
    [all-tokens paired-tokens code-length]
    (let [
          all-and-paired (concat all-tokens paired-tokens)]

        (loop [
               ;; create a transient vector populated with nils
               token-index!
                      (transient (mapv (constantly nil) (range code-length)))
               tokens
                      all-and-paired
            ]

            (if-not tokens
                ; make vector persistent, and return
                (persistent! token-index!)
                (recur
                    (assoc-token
                        token-index!
                        (token->ranges (first tokens))
                        (first tokens)
                        )
                    (next tokens)))
            )))


(defn- set-stylespans [tokens codearea]
    (when-not (empty? tokens)
    (let [
             tokens-and-specs (map (fn [t] [t (->StyleSpec (color t) nil false false)]) tokens)
             spans-builder (StyleSpansBuilder. (* 2 (count tokens)))
             ]

        (loop [prev-end 0 [[token spec] & tokens-and-specs] tokens-and-specs]
            (when-let [{:keys [start end]} token]
                (doto spans-builder
                    ;; add a spacer between tokens
                    (. add (Collections/emptyList) (- start prev-end))
                    ;; then add the token itself
                    (. add (Collections/singleton spec) (- end start))
                    )

                (recur end tokens-and-specs)))

            (fx/thread  ;; should this not go on a channel for speed?!
                (try
                    (. codearea setStyleSpans 0 (. spans-builder create))
                    (catch Exception e (. e printStackTrace))  ;; handle end greater than length.  Fix!
                )
        ))))


(defn- set-spec [codearea code-len ranges spec hover?]
    (doseq [[from to] ranges]
        (when (and from to)  ;; avoid NullPointerException
        (let [
                 ;; ensure bounds
                 new-spec (assoc spec :hover hover?)
                 from (max 0 from)
                 to (min to code-len)
                 ]
                 ;(println "new-spec:" from to new-spec)
                 (fx/thread
                     (try
                         (. codearea setStyle from to new-spec)
                         (catch IllegalArgumentException e (. e printStackTrace)) ;; handle end is greater than length. Fix!
                         )
                     )

            ))))



(defn- color-and-index [codearea code token-index-atom]
    (let [
          code-len
          (. code length)

          singles
          ;(time (tok/tokenize-str code))
          (tok/tokenize-str code)

          _ (set-stylespans singles codearea)

          [paired unpaired]
          (tok/paired-delims (tok/delim-tokens singles))

          unpaired
          (map mark-as-unpaired-delim unpaired)

          _ (doseq [t unpaired]
                (set-spec
                    codearea
                    code-len
                    [[(:start t) (:end t)]]
                    (->StyleSpec (color t) nil false false)
                    false))
        ]

        (go
            (reset! token-index-atom (create-token-index singles paired code-len))
            )))



(defn- codearea-changelistener [codearea token-index-atom]
    (fx/changelistener
        [_ observable old-code new-code]
        (go (color-and-index codearea new-code token-index-atom))))


(defn- set-hover
    [index ^StyledTextArea codearea tokenindexes hover?]
    (when-let [thing (get tokenindexes index)]
        (set-style
            codearea
            (. codearea getLength)
            (token->ranges thing)
            (let [style (. codearea getStyleOfChar index)]
                (if (map? style)  ;; don't know why `(instance? StyleSpec style)` doesn't work! :-(
                    style
                    (first style)  ;; .getStyle... may return java.util.Collections$SingletonSet!
                    ))
            hover?)
        index))


(defn- unhover [index codearea tokenindexes]
    (set-hover index codearea tokenindexes false)
    nil)


(defn- hover [index codearea tokenindexes]
    (set-hover index codearea tokenindexes true))


(defn- mouse-over-handler [codarea tokenindexes-atom]
    (let [last-hovered-index (atom nil)]
        (fx/event-handler-2
            [_ event]
            ;; first "un-hover" the previous thing
            (swap! last-hovered-index unhover codarea @tokenindexes-atom)
            ;; then "hover" the current thing
            (reset! last-hovered-index (hover (. event getCharacterIndex) codarea @tokenindexes-atom))
            )))



(defn ^StyledTextArea ->codearea []
    (let [
          area (code-textarea)

          ;; http://stackoverflow.com/questions/28659716/show-breakpoint-at-line-number-in-richtextfx-codearea
          linenumber-factory (LineNumberFactory/get area)
          _ (.setParagraphGraphicFactory area linenumber-factory)

          token-index (atom []) ;; this will contain references to parse-results by index - for lookups
          hover-handler (mouse-over-handler area token-index)
]
        (doto area
            (.setMouseOverTextDelay (Duration/ofMillis 100))
            (.addEventHandler MouseOverTextEvent/MOUSE_OVER_TEXT_BEGIN hover-handler)
            (-> .textProperty (. addListener (codearea-changelistener area token-index)))
            )))


(defn -main [& args]
    (println "dev.highlight/-main")
    (fx/later
        (doto (fx/stage


            :scene
            (doto
                (fx/scene
                    (fx/stackpane
                        (doto
                            (->codearea)
                            (.replaceText 0 0 (tok/sample-code))))
                    :size [600 400])
                    ; (fx/add-stylesheets "styles/basic.css" "dev/highlight/code.css" )
                    ; (fx/add-stylesheets  "fonts/fonts.css")
                    (fx/add-stylesheets  "styles/codearea.css")
                    )
                )
            :sizetoscene true
            :title "highlight"
            )))


;(-main)