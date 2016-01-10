(ns dev.andante.highlight
    (:require
        [clojure.repl :refer [doc]]
        [clojure.pprint :refer [pp pprint]]
        [clojure.java.io :as cio]

        [clojure.tools.reader :as r]
        [clojure.tools.reader.reader-types :as t]

        [dev.andante.util.java :as j] :reload
        [dev.andante.util.javafx :as fx] :reload
        [dev.andante.util.javafx.classes :as fxc] :reload

        [dev.andante.reader :as my] :reload

        )
    (:import
        [java.util Collections]
        [org.fxmisc.richtext  LineNumberFactory StyledTextArea  MouseOverTextEvent]

        ))



(fxc/import!)

(defn- clamp
    "ensures val does not exceed min or max"
    [min val max]
    (cond
        (< val min) min
        (> val max) max
        :else val))


(defn- read-sample-code[]
    (slurp (cio/resource "dev/highlight/sample_code.clj")))



(defrecord MyStyle [color weight underline hover])


(defn- style->text [^Text text ^MyStyle style]
    (let [{c :color w :weight u :underline h :hover b :background} style]
        (doto text
            (.setStyle
                (str
                    "-fx-fill: " (if h " gold" (if c c "black")) ";"
                    "-fx-font-weight: " (if w w "normal") ";"
                    "-fx-underline: " (if u u "false") ";"
                    "-fx-effect: " (if h "dropshadow(one-pass-box, slategray, 5, 1, 0, 0)" "null") ";"
                    ))
            (.setCursor (if h Cursor/DEFAULT nil))
        )))


(defn- style-text-function []
    (reify
        java.util.function.BiConsumer
        (accept [this text style] ;;  ^Text ^MyStyle
            (style->text text style))))


(defn- my-style-text-area []
    (doto (StyledTextArea.
              (->MyStyle nil nil nil nil)
              (style-text-function))
        (.setStyle "
            -fx-font: 16 'Source Code Pro Medium';
            -fx-padding: 10 5;")

        (.setUseInitialStyleForInsertion true)
        (-> .getUndoManager .forgetHistory)
        (-> .getUndoManager .mark)
        (.selectRange 0 0)

        ))


(defn- set-style [text-area text-length ranges style is-hover]
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


(defn- type->style [typ]
    (println "type->style")
    ;(println "  ## typ:" typ)
    (assoc
        (->MyStyle
            (cond
                (isa? typ java.lang.Number)  "#005d69" ;;  teal
                (#{String Character} typ) "#005500"  ;; green
                (isa? typ java.lang.Iterable)   "blue"
                (= typ clojure.lang.Keyword)   "#9918ff"  ;; dark pink
                (#{clojure.lang.Symbol}  typ) "black"
                (= typ :syntax-error) "#ff5500"  ;; orange
                (= typ :reader-exception) "red"
                :else "orange" )
        nil
        nil
        false)
        :underline (#{:syntax-error :read-exception} typ)))


(defn- assoc-token
    "assocs a given token to every index it covers"
    [rngs token token-index]
    (doseq [rng rngs]
        (doseq [i (apply range rng)]
            (swap! token-index assoc i token))))


(defn- highlight-it [token code-area length token-index]
    (println "highlight-it")
    ;(println "  ## token:" token)

    (when (my/ex-info? token)
        (let [
                 d (.getData token)
                 {from :starting-index to :ending-index typ :type error :error} d
                style (type->style typ)
                 ]
            (println  "  #### ExceptionInfo  type:" type " message:" (.getMessage token) " error:" error " data:" d)
            (when (not= :EOF error)
                (set-style code-area length  (ranges typ from to) style false)

                )
        ))

    (when-let [m (meta token)]
        (println "  ## meta:" m)
        (let [
                 {from :starting-index to :ending-index typ :type} m
                 typ (if typ typ (type token))
                 style (type->style typ)
                 rngs (ranges typ from to)
             ]
            (assoc-token rngs token token-index)
            (set-style code-area length rngs style  false)
            )))


(defn- highlight-them [tokens code-area length token-index]
    (println "highlight-them")
    ;(println "  ## tokens:" tokens)
    (highlight-it tokens code-area length token-index)
    (when (coll? tokens)
        (doseq [token tokens]
            (highlight-them token code-area length token-index))))


(defn- highlight-code [code-area old-code code token-index]
    "a second attempt at a reader/parser/higlighter"
    (if (not= old-code code)
        (let [
                 length (. code length)
                 rdr (my/indexing-pushback-stringreader code)
             ]
            (. code-area clearStyle 0 length)
            (reset! token-index (mapv (constantly nil) (range length)))

            (loop [res nil]
                (when (not= res :eof)
                    (if res (highlight-them res code-area length token-index))
                    (Thread/sleep 10)
                    (let [
                             res (try
                                     (my/read-code rdr)

                                     (catch Exception e
                                         ;(t/read-char rdr)  ;; TODO: fix this hack!
                                         (if (my/syntax-error? e)
                                             (do
                                                 (println "  ## syntax-error: " (my/data e))
                                                 (println " next rdr index:" (.getIndex rdr))
                                                 ;; try step past cause of error to keep reading
                                                 (if (= (-> e my/data :error) my/UNMATCHED_START)
                                                     (do
                                                        (println "  ## stepping back ...")
                                                        (.unreadTo rdr (-> e my/data :ending-index)))
                                                     (do
                                                         (println "  ## stepping forwards ...")
                                                         ;(println "  ## stepping forwards ... NOT")
                                                         (.read rdr)
                                                         ))
                                                 e)
                                             ;; else
                                             (throw e))))
                             ]

                        (recur res)))))))



(defn- code-area-change-listener [code-area token-index]
    (reify ChangeListener
        (changed [_ obs old-txt new-txt]
            (highlight-code code-area old-txt new-txt token-index)
            (doseq [i (range (count @token-index))]
                (let [t (get @token-index i) m (meta t)]
                    (println i ": " (type t) "" m )))
        )))


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
            area (my-style-text-area)
            _ (def area area)
             linenumber-factory (LineNumberFactory/get area)
             _ (.setParagraphGraphicFactory area linenumber-factory)

             sample-code (read-sample-code)

             scene (Scene. (StackPane. (j/vargs area)) 600 400)
             token-index (atom []) ;; this will contain references to parse-results by index - for lookups
            hover-handler (mouse-over-handler area token-index)
]
        ; (fx/add-stylesheets scene "styles/basic.css" "dev/highlight/code.css" )
        (fx/add-stylesheets scene "styles/basic.css" )

        (doto area
            (.setMouseOverTextDelay (java.time.Duration/ofMillis 100))
            (.addEventHandler MouseOverTextEvent/MOUSE_OVER_TEXT_BEGIN hover-handler)
            (-> .textProperty (.addListener (code-area-change-listener area token-index)))
            (.replaceText 0 0 sample-code)
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