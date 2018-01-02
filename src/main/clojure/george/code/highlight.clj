;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.code.highlight
    (:require
        [clojure.repl :refer [doc]]
        [clojure.pprint :refer [pp pprint]]

        [clojure.core.async :refer [go thread chan >! >!! <! <!! go-loop sliding-buffer]]

        [george.javafx :as fx]
        [george.code.reader :as my]
        [george.code.tokenizer :as tok])
    (:import
        [java.time Duration]
        [clojure.lang Keyword Symbol]
        [org.fxmisc.richtext MouseOverTextEvent StyleClassedTextArea]
        [org.fxmisc.richtext.model StyleSpansBuilder]
        [george.code.tokenizer TokenError Arg DelimChar Comment MacroChar MacroDispatchChar]))



(defn- unpaired-delim? [token]
    (boolean (get-in token [:value :unpaired])))


(defn- mark-as-unpaired-delim
  "adds the kv-pair ':unpaired true' to the token-record"
  [token]
  (assoc-in token [:value :unpaired] true))


(def cssclass-dict
    {Symbol "symbol"
     :default  "default"
     Boolean "boolean"
     :nil "nil"
     TokenError "tokenerror"
     :unpaired "unpaired"
     Keyword "keyword"
     Arg "arg"
     String "string"
     Character "character"
     DelimChar "delimchar"  ;; all types of params
     Comment "comment"
     MacroChar "macrochar"
     MacroDispatchChar "macrodispatchchar"})


(defn- cssclass-get
  "Returns the lookup-ed value for the key 'k'."
  [k]
  ;(pprint ["/cssclass" "k:" k])
  (if (isa? k Number)
      "number"
      (cssclass-dict k "unkown")))


(defn- cssclass
  "Returns a Sting naming a css class for the given key."
  [token]
  ;(pprint ["/cssclass" "token:" token "type:" (-> token :value type)])
  (let [
        value (:value token)
        typ
        (cond
            (= :nil value)  :nil
            (unpaired-delim? token)  :unpaired
            :else (type value))]

      (cssclass-get typ)))


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


(defn- assoc-token!
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
                    (assoc-token!
                        token-index!
                        (token->ranges (first tokens))
                        (first tokens))

                    (next tokens))))))


(def ^:private empty-set #{})


(defn- set-stylespans [tokens codearea]
  ;(pprint ["/set-stylespans tokens:" tokens])
  (when-not (empty? tokens)
    (let [
          tokens-and-classes
          (map (fn [t] [t #{(cssclass t)}]) tokens)

          spans-builder
          (StyleSpansBuilder. (* 2 (count tokens)))]

        (loop [prev-end 0
               [[token classes] & tokens-and-classes] tokens-and-classes]

            (when-let [{:keys [start end]} token]
              ;; add a spacer between tokens
               (.add spans-builder empty-set (- start prev-end))
               ;; then add the tokens itself
               (.add spans-builder classes (- end start))

               (recur end tokens-and-classes)))

        (fx/later  ;; TODO: should this not go on a channel for speed?! See example
            (try
                (.setStyleSpans  codearea 0 (.create spans-builder))
                ;; End greater than length.  TODO: Fix!
                (catch Exception e (.printStackTrace e)))))))


;; StyleSpans.mapStyles
(defn- map-styles [styles mapper]
  (let [builder (StyleSpansBuilder. (.getSpanCount styles))]
    (doseq [span (iterator-seq (.iterator styles))]
      (.add builder (mapper (.getStyle span)) (.getLength span)))
    (.create builder)))


(defn set-style-on-range
 ([codearea range ^String style]
  (set-style-on-range codearea range style true))
 ([codearea [start end :as range] ^String style add?]
  (when (not= start end)
    ;(println "/set-style-on-range range:" range "style:" style "add?:" add?)
    (let [old-styles (.getStyleSpans codearea start end)
          new-styles
          (map-styles old-styles
                      #(if (empty? %)
                           (if add? #{style} empty-set)
                           ((if add? conj disj) % style)))]
      ;(println "  ## new-styles:" new-styles)
      (fx/later (.setStyleSpans codearea start new-styles))))))


(defn- color-and-index [codearea code token-indexes_]
  (let [
          code-len
          (.length code)

          singles
          ;(time (tok/tokenize-str code))
          (tok/tokenize-str code)
          ;_ (pprint ["singles" singles])
          _ (set-stylespans singles codearea)

          [paired unpaired] (tok/paired-delims (tok/delim-tokens singles))
          unpaired (filter some? unpaired)
          unpaired (map mark-as-unpaired-delim unpaired)]

    (-> codearea .errorlines (.setValue (into #{} (map :line unpaired))))

    (doseq [t unpaired]
      (fx/thread
        (try
          (set-style-on-range codearea [(:start t) (:end t)] (cssclass t))
          (catch IllegalArgumentException e (.printStackTrace e)))))
            ;; handle end is greater than length. Fix!

    (go (reset! token-indexes_ (create-token-index singles paired code-len)))))



(defn- codearea-changelistener [codearea token-indexes_]
    (fx/changelistener
        [_ _ _ new-code]
        (go (color-and-index codearea new-code token-indexes_))))


(defn- set-hover
    [index codearea tokenindexes hover?]
    (when-let [thing (get tokenindexes index)]
      (doseq [r (token->ranges thing)]
        (set-style-on-range codearea r "hover" hover?))
      (when hover? index)))


(defn set-handlers [codearea]
    (let [
          ;; this will contain references to parse-results by index - for lookups
          tokenindex_ (atom [])

          last-hovered-index_ (atom nil)

          begin-handler
          (fx/event-handler-2
            [_ e]
            ;(println "  ## begin-handler")
            (swap! last-hovered-index_
                   set-hover codearea @tokenindex_ false)
            (reset! last-hovered-index_
                  (set-hover (.getCharacterIndex e) codearea @tokenindex_ true)))

          end-handler
          (fx/event-handler (println "  ## end-handler"))

          changelistener
          (codearea-changelistener codearea tokenindex_)]

        (doto codearea
            (.setMouseOverTextDelay (Duration/ofMillis 100))
            (.addEventHandler MouseOverTextEvent/MOUSE_OVER_TEXT_BEGIN
                              begin-handler)
            ;(.addEventHandler MouseOverTextEvent/MOUSE_OVER_TEXT_END
            ;                  end-handler)

            (-> .textProperty (.addListener changelistener)))))