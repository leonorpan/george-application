(ns george.editor.readers.core
  (:require
    [clojure.pprint :refer [pprint]]
    [george.util :as u]
    [george.util.text :as ut]))


;(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defrecord ReadChar
  ^{:doc "A type for wrapping a char together with its index, col, row in the passed-in string."}
  [^int index location ^char char]
  Object
  (toString [_] (format "(%s) %s '%c'" index location char)))
;(println (str (->ReadChar 1 [2 3] \4)))


(defrecord Block
  ^{:doc "A type for wrapping a char.
  'span' is [start-index end-index]
  'start' and 'end' are locations [row col] of first and last char.
  'chars' are [start-char end-char]."}
  [span first last chars]
  Object
  (toString [_] (format "%s [%s %s] %s" span first last chars)))


(defn read-it
  "Returns a seq of ReadChars."
  [s]
  (loop [i 0
         [^int row ^int col] [0 0]
         chars (seq s)
         res (list)]
    (if-let [ch (first chars)]
      (let [rc (->ReadChar i [row col] ch)]
        ;(println (str rc))
        (recur (inc i)
               (if (ut/newline-char? ch) [(inc row) 0] [row (inc col)])
               (rest chars)
               (cons rc res)))
      (reverse res))))


(defn- push-or-pair
  "Returns a 2-element vector containing:
  - the (possibly) altered stack
  - a 2-element vector of paired ReadChars, or nil
  Takes an existing stack (list) and a ReadChar"
  [stack RD]
  (if
    (and
      (not-empty stack)
      (ut/coll-delim-char-matches?  (.char ^ReadChar (first stack)) (.char ^ReadChar RD)))
    [(rest stack) [(first stack) RD]]
    [(cons RD stack) nil]))

;; TODO: Ignore everything while in string or line-comment.
(defn pair-coll-delims
  "Returns a 2-element vector containing:
  - a vector of 2-element vectors of paired ReadChars that are coll-delims
  - a vector of any unpaired ReadChars that are coll-delims
  Takes a seq of ReadChars"
  [RDs]
  (loop [RDs (filter #(ut/coll-delim-char? (.char ^ReadChar %)) RDs)
         stack (list)
         pairs []]
    (if-let [RD (first RDs)]
      (let [[stack pair] (push-or-pair stack RD)]
        (recur
          (rest RDs)
          stack
          (if pair (conj pairs pair) pairs)))
      [pairs (vec stack)])))  ;; whatever was left on the push-stack is "unpaired"


(defn read-and-pair-coll-delims [code-str]
  (pair-coll-delims (read-it code-str)))


(defn- pairs->block [[^ReadChar a ^ReadChar b]]
  (->Block [(.index a) (.index b)]
           (.location a)
           (.location b)
           [(.char a) (.char b)]))


(defn block-spans [code-str]
  (let [[pairs _](read-and-pair-coll-delims code-str)]
    (sort-by #(first (.span ^Block %)) (map pairs->block pairs))))


;; DEV

(def sample-code "(defn foo\n  \"hello, this is a docstring\"\n  [a b]\n  (let [sum (+ a b)\n        prod (* a b)]\n     {:sum sum\n      :prod prod}))")

;(pprint (time (read-and-pair-coll-delims sample-code)))
;(pprint (time (block-spans sample-code)))