;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.code.highlight
    (:require
        [clojure.repl :refer [doc]]
        [clojure.pprint :refer [pp pprint]]

        [clojure.core.async :refer [go thread chan >! >!! <! <!! go-loop sliding-buffer]]

        [george.javafx :as fx]
        [george.code.reader :as my]
        [george.code.tokenizer :as tok]
        [george.code.codearea :as ca])
    (:import
        [java.util Collections]
        [clojure.lang Keyword Symbol]
        [org.fxmisc.richtext StyledTextArea MouseOverTextEvent StyleSpansBuilder]
        [george.code.tokenizer TokenError Arg DelimChar Comment MacroChar MacroDispatchChar]

        [java.time Duration]))


(defn- clamp
    "ensures val does not exceed min or max"
    [min val max]
    (cond
        (< val min) min
        (> val max) max
        :else val))





(defn- background-fill [hover?]
    (if hover? "yellow" nil))


(defn- set-style [codearea code-len ranges style hover?]
    (doseq [[from to] ranges]
        (when (and from to)  ;; skip [nil nil] ranges.  Fix!
            (let [
                 ;; ensure bounds
                  new-style  (assoc style :background (background-fill hover?))
                  from (max 0 from)
                  to (min to code-len)]

;            (println "new-style:" from to new-style)
             (. codearea setStyle  from to new-style)))))





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
            "orange"))



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
                    (type value))]

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
                  [] rngs)]

         (loop [indv index-vector!
                inds indexes]
             (if (empty? inds)
                 indv
                 (recur
                     (assoc! indv (first inds) token)
                     (rest inds)))))))





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
                      all-and-paired]


            (if-not tokens
                ; make vector persistent, and return
                (persistent! token-index!)
                (recur
                    (assoc-token
                        token-index!
                        (token->ranges (first tokens))
                        (first tokens))

                    (next tokens))))))



(defn- set-stylespans [tokens codearea]
    (when-not (empty? tokens)
     (let [
              tokens-and-specs (map (fn [t] [t (ca/->StyleSpec (color t) nil false false)]) tokens)
              spans-builder (StyleSpansBuilder. (* 2 (count tokens)))]


         (loop [prev-end 0 [[token spec] & tokens-and-specs] tokens-and-specs]
             (when-let [{:keys [start end]} token]
                 (doto spans-builder
                    ;; add a spacer between tokens
                     (. add (Collections/emptyList) (- start prev-end))
                    ;; then add the token itself
                     (. add (Collections/singleton spec) (- end start)))


                 (recur end tokens-and-specs)))

         (fx/thread  ;; should this not go on a channel for speed?!
             (try
                 (. codearea setStyleSpans 0 (. spans-builder create))
                 (catch Exception e (. e printStackTrace)))))))  ;; handle end greater than length.  Fix!




(defn- set-spec [codearea code-len ranges spec hover?]
    (doseq [[from to] ranges]
        (when (and from to)  ;; avoid NullPointerException
         (let [
                 ;; ensure bounds
                  new-spec (assoc spec :hover hover?)
                  from (max 0 from)
                  to (min to code-len)]

                 ;(println "new-spec:" from to new-spec)
              (fx/thread
                  (try
                      (. codearea setStyle from to new-spec)
                      (catch IllegalArgumentException e (. e printStackTrace)))))))) ;; handle end is greater than length. Fix!







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
          (map mark-as-unpaired-delim unpaired)]


        (. (. codearea errorlines)
           setValue
           (into #{} (filter some? (map :line unpaired))))

        (doseq [t unpaired]
            (set-spec
                codearea
                code-len
                [[(:start t) (:end t)]]
                (ca/->StyleSpec (color t) nil false false)
                false))

        (go
            (reset! token-index-atom (create-token-index singles paired code-len)))))




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
                    (first style)))  ;; .getStyle... may return java.util.Collections$SingletonSet!

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
            (reset! last-hovered-index (hover (. event getCharacterIndex) codarea @tokenindexes-atom)))))




(defn set-handlers [^StyledTextArea codearea]
    (let [
          token-index (atom []) ;; this will contain references to parse-results by index - for lookups
          hover-handler (mouse-over-handler codearea token-index)]

        (doto codearea
            (. setMouseOverTextDelay (Duration/ofMillis 100))
            (. addEventHandler MouseOverTextEvent/MOUSE_OVER_TEXT_BEGIN hover-handler)
            (-> .textProperty (. addListener (codearea-changelistener codearea token-index))))))

