;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.code.tokenizer
    (:require

        [clojure.core.async :refer [go thread chan >! >!! <! <!! go-loop]]

        [clojure.tools.reader.impl.commons :refer
         [number-literal? match-number parse-symbol read-past skip-line]]

        [clojure.repl :refer [doc]]
        [clojure.pprint :refer [pp pprint]]

        [clojure.java.io :as cio])
    (:import (clojure.lang LineNumberingPushbackReader)))






(declare
    read-char
    unread-char
    peek-char
    macros)




(def INVALID_NUMBER :invalid-number)
(def INVALID_CHAR :invalid-character)
(def INVALID_ESCAPE :invalid-escape-character)
(def INVALID_TOKEN :invalid-token)
(def INVALID_META :invalid-meta)
(def INVALID_FEATURE :invalid-feature)
(def INVALID_SPLICE :invalid-splice)
(def INVALID_ARG :invalid-arg)
(def EOF :EOF)
(def UNMATCHED_START :unmatched-start-delim)
(def UNMATCHED_END :unmatched-end-delim)
;(def UNEVEN_NUMBER :uneven-number-of-forms)
(def UNMATCHED_KEY_VALUE_PAIR :unmatched-key-value-pair)
(def UNMATCHED_COND :unmatched-cond)




(definterface IIndex
    (^int getIndex []))


(defn indexing-pushback-stringreader [s]
    (let [indx (atom 0) s-len (. s length)]
        (proxy [LineNumberingPushbackReader
                IIndex] [(java.io.StringReader. s)]
            (read []
                (if (< @indx s-len)
                    (do
                        (swap! indx inc)
                        (proxy-super read))
                    -1))
            (unread
                ([ch]
                 (proxy-super unread (int ch))
                 (swap! indx dec)
                 nil)
                ([^chars cbuf off len]
                 (throw
                     ;; TODO: convert this to multiple calls to unread(ch), if necessary
                     (Exception. "LineNumberingPushbackReader can't unread multiple chars!"))

                 (proxy-super unread cbuf off len)
                 (reset! indx (- @indx (- len off)))))
            (getIndex []
                @indx))))



(defn- repr [value]
    (cond
        (nil? value) "nil"
        (= value \newline)     "\\newline"
        (= value \space)       "\\space"
        (= value \tab)         "\\tab"
        (= value \backspace)   "\\backspace"
        (= value \formfeed)    "\\formfeed"
        (= value \return)      "\\return"
        (instance? Character value) (str \\ value)
        (instance? String value) (str \" value \")
        :else (str value)))


(defrecord Token [start end value]
    ;; value is either a resulting value, or a TokenError-record
    ;; highlighting et al will have to figure out the type and what to do with it
    Object
    (toString [_]  (str "Token: [" start " " end "]  " (repr (type value)) ": " (repr value))))

(defrecord TokenError [type message]
    Object (toString [_] (str "type: " type "  message: " message)))



(defrecord DelimChar [^char value]  ;;  \{ \[ \( \) \] \}
    Object (toString [_] (repr value)))

(defrecord MacroChar [^char value]
    Object (toString [_] (repr value)))

(defrecord MacroDispatchChar [^char value]
    Object (toString [_] (repr value)))

(defrecord Comment [^String value]
    Object (toString [_] (repr value)))

(defrecord Arg [^String value]
    Object (toString [_] (str value)))






;;;;;;;; utils ;;;;;;;;

(defn whitespace? [ch]
    (when ch
        (or (Character/isWhitespace ^Character ch)
            (identical? \,  ch))))


(defn- read-unicode-char
    ([^String token offset length base]
     (let [l (+ offset length)]
        (when-not (== (count token) l)
            (throw (IllegalArgumentException. (str "Invalid unicode character: \\" token))))
        (loop [i offset uc 0]
            (if (== i l)
                (char uc)
                (let [d (Character/digit (int (nth token i)) (int base))]
                    (if (== d -1)
                        (throw (IllegalArgumentException. (str "Invalid digit: " (nth token i))))
                        (recur (inc i) (long (+ d (* uc base))))))))))

    ([rdr initch base length exact?]
     (loop [i 1 uc (Character/digit (int initch) (int base))]
        (if (== uc -1)
            (throw (IllegalArgumentException. (str "Invalid digit: " initch)))
            (if-not (== i length)
                (let [ch (peek-char rdr)]
                    (if (or (whitespace? ch)
                            (macros ch)
                            (nil? ch))
                        (if exact?
                            (throw (IllegalArgumentException.
                                       (str "Invalid character length: " i ", should be: " length)))
                            (char uc))
                        (let [d (Character/digit (int ch) (int base))]
                            (read-char rdr)
                            (if (== d -1)
                                (throw (IllegalArgumentException. (str "Invalid digit: " ch)))
                                (recur (inc i) (long (+ d (* uc base))))))))
                (char uc))))))







(defn numeric? [^Character ch]
    (when ch
        (Character/isDigit ch)))


(defn whitespace? [ch]
    (when ch
        (or (Character/isWhitespace ^Character ch) (identical? \,  ch))))


(defn- normalize-newline [rdr ch]
    (if (identical? \return ch)
        (let [c (peek-char rdr)]
            (when (or (identical? \formfeed c) (identical? \newline c))
              (read-char rdr))
            \newline)
        ch))


(defn- read-char [rdr]
    (let [c (.read rdr)]
        (when (>= c 0)
            (normalize-newline rdr (char c)))))


(defn- unread-char [rdr c]
    (when c
        (. rdr unread (int c))))


(defn- peek-char [rdr]
    (when-let [c (read-char rdr)]
        (unread-char rdr c)
        c))


(defn newline?
    "Checks whether the character is a newline"
    [c]
    (or (identical? \newline c)
        (nil? c)))


(defn read-comment [rdr initch]
    (let [start-index (dec (.getIndex rdr))]
        (loop [sb (StringBuilder.) ch initch]
            (if (newline? ch)
                (Token. start-index (.getIndex rdr) (Comment. (str sb)))
                (recur (doto sb (.append ch)) (read-char rdr))))))


(def ^:dynamic *alias-map*
    "Map from ns alias to ns, if non-nil, it will be used to resolve read-time
     ns aliases instead of (ns-aliases *ns*).

     Defaults to nil"
    nil)


(defn- resolve-ns [sym]
    (or ((or *alias-map*
             (ns-aliases *ns*)) sym)
        (find-ns sym)))


(defn- macro-terminating? [ch]
    (case ch
        (\" \; \@ \^ \` \~ \( \) \[ \] \{ \} \\) true
        false))


(defn- ^String read-token
    "Read in a single logical token from the reader"
    [rdr initch]
    ;; This really isn't an error.  It is simply the end of the file!
    ;(if-not initch (reader-error (str rdr "EOF while reading"))
;; TODO: remove the if-not ...
    (if-not initch
        ;(throw-syntax-error EOF "EOF while reading token")
        ""
        (loop [sb (StringBuilder.) ch initch]
            ;(prn "ch:" ch)
            (if (or (whitespace? ch) (macro-terminating? ch) (nil? ch))
                (do
                    (when ch (unread-char rdr ch))
                    (str sb))
                (recur (.append sb ch) (read-char rdr))))))



(defn- read-keyword [rdr _]
    (let [
          start-index (dec (. rdr getIndex))
          ch (read-char rdr)

          res
          (if (whitespace? ch)
            (TokenError. INVALID_TOKEN "Whitespace not allowed in keyword")
            ;; else
            (let [token (read-token rdr ch) s (parse-symbol token)]
                (if-not s
                    (TokenError. EOF nil)
                    ;; else
                    (let [^String ns (s 0) ^String name (s 1)]
                        (if-not (identical? \: (nth token 0))
                            (keyword ns name)
                            ;; else
                            (if-not ns
                                (keyword (str *ns*) name)
                                ;; else
                                (if-let [ns (resolve-ns (symbol (subs ns 1)))]
                                    ;; then
                                    (keyword (str ns) name)
                                    ;; else
                                    (TokenError. INVALID_TOKEN (str "Invalid token: ':" token "' (resolve-ns returned 'nil')")))))))))]

             ; we only care about the )

        (Token. start-index (. rdr getIndex) res)))




;; TODO: what/why is this?!?
(def ^:private ^:const upper-limit (int \uD7ff))
(def ^:private ^:const lower-limit (int \uE000))


(defn- parse-unicode [token]
    (let [c (read-unicode-char token 1 4 16) ic (int c)]
        (if (and (> ic upper-limit) (< ic lower-limit)) ;; TODO: what/why is this?!?
            (TokenError. INVALID_CHAR (str "Invalid character constant: \\u" (Integer/toString ic 16)))
            c)))


(defn- parse-octal [token token-len]
    (let [len (dec token-len)]
        (if (> len 3)
            (TokenError. INVALID_CHAR (str "Invalid octal escape sequence length: " len))
            (let [uc (read-unicode-char token 1 len 8)]
                (if (> (int uc) 0377)
                    (TokenError. INVALID_CHAR "Octal escape sequence must be in range [0, 377]")
                    uc)))))


(defn- read-char*
    "Read in a character literal"
    [rdr _]
    (let [
            start-index (dec (. rdr getIndex))
            ch (read-char rdr)
            value
                (if (nil? ch)
                    (TokenError. EOF "EOF while reading character")
                    ;; else
                    (let [
                          token
                          (if (or (macro-terminating? ch) (whitespace? ch))
                              (str ch)
                              (read-token rdr ch))
                          token-len
                          (count token)]

                        (cond
                            (== 1 token-len)        (Character/valueOf (nth token 0))
                            (= token "newline")     \newline
                            (= token "space")       \space
                            (= token "tab")         \tab
                            (= token "backspace")   \backspace
                            (= token "formfeed")    \formfeed
                            (= token "return")      \return
                            (.startsWith token "u") (parse-unicode token)
                            (.startsWith token "o") (parse-octal token token-len)
                            :else  (TokenError. INVALID_CHAR (str "Unsupported character: \\" token)))))]

        (Token. start-index (. rdr getIndex) value)))



(defn- escape-char [sb rdr]
    (let [ch (read-char rdr)]
        (case ch
            \t "\t"
            \r "\r"
            \n "\n"
            \\ "\\"
            \" "\""
            \b "\b"
            \f "\f"
            \u
            (let [ch (read-char rdr)]
                (if (== -1 (Character/digit (int ch) 16))
                    ; TODO: perhaps unify these errors and add start and end
                    (Token. -1 -1 (TokenError. INVALID_ESCAPE (str "Invalid unicode escape: \\u" ch)))
                    ;else
                    (read-unicode-char rdr ch 16 4 true)))
            ;default
            (if (numeric? ch)
                (let [ch (read-unicode-char rdr ch 8 3 false)]
                    (if (> (int ch) 0337)
                        (Token. -1 -1 (TokenError. INVALID_ESCAPE "Octal escape sequence must be in range [0, 377]"))
                        ch))
                (Token. -1 -1 (TokenError. INVALID_ESCAPE (str "Unsupported escape character: \\u" ch)))))))





(defn- read-string* [rdr _]
    (let [
             start-index (dec (. rdr getIndex))]


        (loop [sb (StringBuilder.) ch (read-char rdr)]
            (case ch

                nil
                ;(Token. start-index (. rdr getIndex) (TokenError. EOF "EOF while reading string"))
                (Token. start-index (. rdr getIndex) (str sb))
                \\
                (let [r (escape-char sb rdr)]
                    (if (instance? Token r)
                        r
                        (recur (doto sb (.append r)) (read-char rdr))))
                \"
                (Token. start-index (. rdr getIndex) (str sb))

                ;default
                (recur (doto sb (.append ch)) (read-char rdr))))))




(defn- read-number
    [rdr initch]
    (let [start-index (dec (. rdr getIndex))]
        (loop [sb (doto (StringBuilder.) (.append initch)) ch (read-char rdr)]
            (if (or (whitespace? ch) (macros ch) (nil? ch))
                (let [s (str sb)]
                    (unread-char rdr ch)
                    (Token. start-index (. rdr getIndex)
                        (if-let [v (match-number s)]
                             v
                            (TokenError. INVALID_NUMBER (str "Invalid number format [" s "]")))))
                (recur (doto sb (.append ch)) (read-char rdr))))))



(defn-
    read-delim-char
    [rdr ch]
    (assoc (Token. (dec (. rdr getIndex)) (. rdr getIndex) (DelimChar. ch))
        :line (. rdr getLineNumber)))


(defn-
    read-macro-char
    [rdr ch]
    (Token. (dec (. rdr getIndex)) (. rdr getIndex) (MacroChar. ch)))

(defn-
    read-macro-dispatch-char
    [rdr ch]
    (Token. (dec (. rdr getIndex)) (. rdr getIndex) (MacroDispatchChar. ch)))

(defn- parse-integer [s]
    (try
        (Integer/parseInt (str s))
        (catch NumberFormatException nfe nil)))

(defn- read-arg [rdr pct]
        (let [
                 start-index (dec (. rdr getIndex))
                 sb (StringBuilder. (str pct))
                token
                (loop  [sb sb ch (read-char rdr)]
                    (cond
                        (or (whitespace? ch) (macro-terminating? ch) (nil? ch))
                        (do
                          (unread-char rdr ch)
                          (Arg. (str sb)))

                        (identical? ch \&)
                        (Arg. (str sb))

                        (not (parse-integer ch))
                        (TokenError. INVALID_ARG (str "Arg literal must be %, %& or %integer.  Found: " sb))

                        :else
                        (recur (.append sb ch) (read-char rdr))))]

            (Token. start-index (. rdr getIndex) token)))


(defn- macros [ch]
    (case ch
        \" read-string*
        \: read-keyword
        \; read-comment
        \\ read-char*
        \% read-arg
        \# read-macro-dispatch-char
        (\' \^ \@ \` \~)    read-macro-char
        (\( \[ \{ \) \] \}) read-delim-char
        nil))



(defn- read-symbol
    [rdr initch]
    (let [start-index (dec (. rdr getIndex))]
        (when-let [token (read-token rdr initch)]
            (Token. start-index (. rdr getIndex)
                (case token
                    ;; special symbols
                    "nil" :nil ;nil
                    "true" true
                    "false" false
                    "/" '/
                    "NaN" Double/NaN
                    "-Infinity" Double/NEGATIVE_INFINITY
                    ("Infinity" "+Infinity") Double/POSITIVE_INFINITY
                    ;; default
                    (try

                        (if-let [p (parse-symbol token)]
                            (symbol (p 0) (p 1))
                            (TokenError. INVALID_TOKEN (str "Invalid token: " token " (at 'read-token')")))

                        (catch Exception e
                            (throw e))))))))





;;;;;;;; reading ;;;;;;;;



(defn tokenize [rdr] ;; indexing-pushback-reader
    (let [tokens (transient [])]
        (loop []
            (let [ch (read-char rdr)]
                (cond
                    (nil? ch)  ;; end of stream
                    (persistent! tokens)

                    (whitespace? ch)
                    (recur)

                    (number-literal? rdr ch)
                    (do (conj! tokens (read-number rdr ch))
                        (recur))

                    :else
                    (let [f (macros ch)
                          res (if f
                                  (let [res (f rdr ch)]
                                      (if-not (identical? res rdr)
                                          res))
                                  (read-symbol rdr ch))]

                        (if res (conj! tokens res))
                        (recur)))))))



(defn tokenize-str [clj-str]
    (tokenize (indexing-pushback-stringreader clj-str)))


(defn sample-code []
    (slurp (cio/resource "dev/highlight/sample_code.clj")))




(defn delim-tokens [tokens]
    (filter #(instance?  DelimChar (:value %)) tokens))

(defn non-delim-tokens [tokens]
    (filter #(not (instance?  DelimChar (:value %))) tokens))


(defn- matching-end-delim-char [delim-ch]
    (case delim-ch
        \( \)
        \[ \]
        \{ \}
        nil))


(defn delim-char [token]
    "returns delim-char if delim, else nil"
    (if (instance? Token token)
        (recur (:value token))
        (when (instance? DelimChar token)
            (:value token))))


(defn start-delim? [token]
    "true if start-delim, false if end-delim, else nil"
    (when-let [ch (delim-char token)]
        (case ch
            (\( \[ \{) true
            false)))


(defn- pair? [t1 t2]
    (= (matching-end-delim-char (delim-char t1)) (delim-char t2)))


(defn- pair [t1 t2]
    "returns a pair, if pair? else nil"
    (when (pair? t1 t2)
        [t1 t2]))


(defn- push-or-pair [start-stack t]
    "returns start-stack and pair - pushing/poping stack, and pair or nil"
    (if (empty? start-stack)
        [(conj start-stack t) nil]
        (if-let [p (pair (first start-stack) t)]
            [(rest start-stack) p]
            [(conj start-stack t) nil])))



(defn paired-delims [delim-tokens]
    "returns vectors in a vector -
    the first containing pairs of delim-tokens, the second containing unpaired delim-tokens"
    (loop [
            start-stack (list)
            paired []
            unpaired []
            lst delim-tokens]

        (if-not lst
            [paired (concat unpaired start-stack)]
            (let [
                    [start-stack a-pair] (push-or-pair start-stack (first lst))
                    paired (if a-pair (conj paired a-pair) paired)]

                (recur start-stack paired unpaired (next lst))))))


;(doseq  [token (tokenize-str (sample-code))]
;    (println (str token)))

;(doseq  [token (delim-tokens (tokenize-str (sample-code)))]
;    (println (str token)))

;(let [[paired unpaired] (paired-delims (delim-tokens (tokenize-str (sample-code))))]
;    (doseq [t paired]   (println "  paired:" (str t)))
;    (doseq [t unpaired] (println "unpaired:" (str t))))




;(defn print-chan []
;    (let[in (chan 1)]
;        (go
;            (while true
;                (let [s (<! in)]
;                    (println "s:" s))))
;    in ))
;
;(let [c (print-chan)]
;    (>!! c "Hello")
;    (>!! c "world.")
;
;    (>!! c "How")
;    (>!! c "are")
;    (>!! c "you?")
;    )