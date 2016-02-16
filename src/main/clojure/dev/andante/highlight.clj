(ns dev.andante.highlight
    (:require
        [clojure.repl :refer [doc]]
        [clojure.pprint :refer [pp pprint]]
        [clojure.java.io :as cio]

        [clojure.core.async :refer [go thread chan >! >!! <! <!! go-loop sliding-buffer]]

;        [clojure.tools.reader :as r]
;        [clojure.tools.reader.reader-types :as t]

        [george.java :as j] :reload
        [george.javafx :as fx] :reload
        [george.javafx-classes :as fxc] :reload

        [dev.andante.reader :as my] :reload
        [dev.andante.tokenizer :as tok] :reload
        )
    (:import
        [java.util Collections]
        [org.fxmisc.richtext  LineNumberFactory StyledTextArea  MouseOverTextEvent StyleSpansBuilder]

        ))



(fxc/import-classes)

(defn- clamp
    "ensures val does not exceed min or max"
    [min val max]
    (cond
        (< val min) min
        (> val max) max
        :else val))


(defrecord StyleSpec [color weight underline hover])


(defn- ^String style [^StyleSpec spec]
    (let [{c :color w :weight u :underline h :hover b :background} spec]
            (str
                "-fx-fill: " (if h " gold" (if c c "black")) "; "
                "-fx-font-weight: " (if w w "normal") "; "
                "-fx-underline: " (if u u "false") "; "
                "-fx-effect: " (if h "dropshadow(one-pass-box, slategray, 5, 1, 0, 0)" "null") "; "
            )))




(defn- apply-specs [^Text text specs]
    (println "apply-spec  specs:" specs)
    (if (instance? StyleSpec specs)
        (. text setStyle (style specs))
        (doseq [spec specs]
            (. text setStyle (style spec))))


    ;(.setCursor (if h Cursor/DEFAULT nil))
)


(defn- style-biconsumer []
    (reify java.util.function.BiConsumer
        (accept [this text style]
            (apply-specs text style))))



(defn- code-textarea []
    (doto (StyledTextArea.
              (StyleSpec. nil nil nil nil)
              (style-biconsumer))
        (.setStyle "
            -fx-font: 16 'Source Code Pro Medium';
            -fx-padding: 10 5;
            -fx-background-color: #E6E6E6;"
        )

        (.setUseInitialStyleForInsertion true)
        (-> .getUndoManager .forgetHistory)
        (-> .getUndoManager .mark)
        (.selectRange 0 0)
    ))



(defn- set-style [text-area text-length ranges style is-hover]
    (println "ranges:" ranges)
    (doseq [[from to] ranges]
        (let [
                 ;; ensure bounds
                 new-style  (assoc style :hover is-hover)
                 from (max 0 from)
                 to (min to text-length)
                 ]
            (println "new-style:" from to new-style)
            (.setStyle text-area from to new-style)

            )))


(comment defn- style-class [area  length css-class ranges]
    (doseq [[from to] ranges]
        (let [
                ;; ensure bounds
                 from (max 0 from)
                 to (min to length)
             ]
            (println "style-class:" css-class from to)
            (.setStyleClass area from to css-class))))


(defn- ranges [typ from to]
    (cond
        (isa? typ clojure.lang.IPersistentSet) [[from (+ 2 from)] [(dec to) to]]
        (isa? typ java.lang.Iterable) [[from (inc from)] [(dec to) to]]
        :else [[from to]]
    ))



(defn- unpaired-delim? [token]
    (println "unpaired?" token)
    (boolean (get-in token [:value :unpaired])))

(defn- unpaired-delim [token]
    (assoc-in token [:value :unpaired] true))


(defn- color [token]
    (let [
             value (:value token)
            typ (case value  :nil value (type value))
         ]
        (println "color typ:" typ)
        (cond
        ;; cursor color? "#26A9E1"

            (or
                (#{
                     dev.andante.tokenizer.TokenError
                     }  typ)
                (unpaired-delim? token))
            "#ED1C24";"red";"#B3191F";


            (isa? typ Number)
            "#524EAC";"#005d69";"#26A9E1";"#00ACEE";"#6897bb";"#262161";"#4a0042";"#336699"

            (#{
                 clojure.lang.Keyword
                 dev.andante.tokenizer.Arg
                  } typ)
            "#9E1F64"

            (#{
                 Boolean
                 :nil } typ)
            "#4F7BDE";"#524EAC";"#1FAECE";"#00ACEE";"#31B2F4"
            (#{
                 String
                 Character} typ)
            "#008e00";"#008000"

            (#{dev.andante.tokenizer.DelimChar} typ)
            "#99999c";"#D1D2D4";"lightgray"
            ; "#f2c100" ;; yellow
            (#{
                 clojure.lang.Symbol

             }  typ)
            "#404042";"#01256e";"#333"

            (#{dev.andante.tokenizer.Comment}  typ)
            "#708080"

            (#{
                 dev.andante.tokenizer.MacroChar
                 dev.andante.tokenizer.MacroDispatchChar

                 }  typ)
            "#cc7832";"#c35a00";"#A33EFE"


            :else
            "orange"
        )))


;        :underline
;        (boolean (#{
;             dev.andante.tokenizer.TokenError
;             :unpaired-delim
;         } typ))



(defn- assoc-token
    "assocs a given token to every index it covers"
    [rngs token token-index]
    (doseq [rng rngs]
        (doseq [i (apply range rng)]
            (swap! token-index assoc i token))))


(defn- token-range [token]
    [(:start token) (:end token)])


(defn- token-ranges [thing]
    "takes a pair of tokens in a vector or a single token and returns a vector of vectors [start end]"
    ;(println "token-ranges thing:" thing)
    (cond
        (vector? thing)
        [(token-range (first thing)) (token-range (second thing))]

        :else
        [(token-range thing)]))



(defn- assoc-token!
    "assocs a given token to every index it covers"
    [rngs token index-vector!]
    ;(println "rngs:" rngs "  token:" token)
    (let [
             indexes
             (reduce
                 (fn [acc rng] (concat acc (apply range rng)))
                 [] rngs)
        ]
        ;(println "indexes:" indexes)
        (loop [indv index-vector! inds indexes]
            ;(println "inds:" inds)
            (if (empty? inds)
                indv
                (recur (assoc! indv (first inds) token) (rest inds))))))



(defn- token-index [tokens code-length]
    (let [
            delims (tok/delim-tokens tokens)
            non-delims (tok/non-delim-tokens tokens)
            all (concat non-delims delims)
            [paired-delims unpaired-delims] (tok/paired-delims delims)
        ]
        (loop [
                ;; create a transient vector populated with nils
                index! (transient (mapv (constantly nil) (range code-length)))
                lst all
            ]
            (if-not lst
                (persistent! index!)
                (recur
                    (assoc-token!
                        (token-ranges (first lst))
                        (first lst)
                        index!)
                    (next lst))))))




(defn- set-stylespans [tokens codearea]
    (let [
             tokens-and-specs (map (fn [t] [t (StyleSpec. (color t) nil false false)]) tokens)
             ;_ (doseq  [[tk sp] tokens-and-specs] (println)(println tk) (println sp))

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

        (fx/thread (. codearea setStyleSpans 0 (. spans-builder create)))))


(defn- set-spec [codearea code-len ranges spec hover?]
    (println "ranges:" ranges)
    (doseq [[from to] ranges]
        (let [
                 ;; ensure bounds
                 new-spec (assoc spec :hover hover?)
                 from (max 0 from)
                 to (min to code-len)
                 ]
;            (println "new-spec:" from to new-spec)
            (fx/thread (. codearea setStyle from to new-spec))

            )))


(defn- color-and-index [codearea code codearea token-index-atom]
    (let [
            code-len (. code length)
            tokens (tok/tokenize-str code)
            _ (set-stylespans tokens codearea)

            [paired unpaired] (tok/paired-delims (tok/delim-tokens tokens))
            unpaired (map unpaired-delim unpaired)

            _ (doseq [t unpaired]
                  (set-spec
                      codearea code-len
                      [[(:start t) (:end t)]]
                      (StyleSpec. (color t) nil false false)
                      false))
        ]
    ))

(defn- codearea-changelistener [codearea token-index-atom]
    (let [
             ;i-chan (index-channel codearea token-index-atom)
         ]
    (reify ChangeListener
        (changed [_ obs old-code new-code]
            (println "change detected ...")
            ;(go (>! i-chan new-txt))

            (go (color-and-index codearea new-code codearea token-index-atom))

;            (highlight-code code-area old-txt new-txt token-index)
;            (doseq [i (range (count @token-index))]
;                (let [t (get @token-index i) m (meta t)]
;                    (println i ": " (type t) "" m )))
        ))))


(defn- token->ranges [token]
    (let [
             m (meta token)
             {from :starting-index to :ending-index typ :type} m
             typ (if typ typ (type token))
         ]
         (ranges typ from to)))


(defn- mouse-over-handler [text-area token-index ]
    (let [last-hovered-index (atom nil)]

        (fx/event-handler-2 [_ event]
            (when-let [i @last-hovered-index]
                (let [
                        old-style (. text-area getStyleOfChar i)
                        token (get @token-index i)
                    ]
                    (reset! last-hovered-index nil)
                    (set-style text-area (. text-area getLength) (token->ranges token) old-style false)

                    ))

            (let [
                    i (. event getCharacterIndex)
                    old-style (. text-area getStyleOfChar i)
                    token (get @token-index i)
                ]
                (reset! last-hovered-index i)
                (set-style text-area (. text-area getLength) (token->ranges token) old-style true)

            ))))



(defn- build-scene []
    (let [
            area (code-textarea)
            _ (def area area)
             linenumber-factory (LineNumberFactory/get area)
             _ (.setParagraphGraphicFactory area linenumber-factory)

             code (tok/sample-code)

             scene (Scene. (StackPane. (j/vargs area)) 600 400)
             token-index (atom []) ;; this will contain references to parse-results by index - for lookups
            hover-handler (mouse-over-handler area token-index)
]
        ; (fx/add-stylesheets scene "styles/basic.css" "dev/highlight/code.css" )
        (fx/add-stylesheets scene "fonts/fonts.css")

        (doto area
            (.setMouseOverTextDelay (java.time.Duration/ofMillis 100))
            ;(.addEventHandler MouseOverTextEvent/MOUSE_OVER_TEXT_BEGIN hover-handler)
            (-> .textProperty (.addListener (codearea-changelistener area token-index)))
            (.replaceText 0 0 code)
        )

        scene ))


(defn -main [& args]
    (println "dev.highlight/-main")
    (fx/dont-exit!)
    (fx/thread
        (doto (Stage.)
            (.setScene (build-scene))
            (.sizeToScene)
            (.setTitle "highlight")
            (.show))))


(fx/init)


(-main)