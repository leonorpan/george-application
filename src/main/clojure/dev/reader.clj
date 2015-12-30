(ns dev.reader

    (:require
              [clojure.tools.reader.impl.commons :refer [
                number-literal? match-number parse-symbol read-past skip-line
                ]]
              [clojure.tools.reader :as ctr]
              [clojure.tools.reader.reader-types :as ctrt]

              )

    (:import (clojure.lang PersistentHashSet IMeta
                                             RT Symbol Reflector Var IObj
                                             PersistentVector IRecord Namespace)
             java.lang.reflect.Constructor
             (java.util regex.Pattern List LinkedList))


    )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare
    ^:private read*
    read-char
    unread
    peek-char
    macros
    dispatch-macros
;    ^:dynamic *read-eval*
;    ^:dynamic *data-readers*
;    ^:dynamic *default-data-reader-fn*
;    ^:dynamic *suppress-read*
;    default-data-readers
    )

(defn numeric?
    "Checks whether a given character is numeric"
    [^Character ch]
    (when ch
        (Character/isDigit ch)))


(defn whitespace?
    "Checks whether a given character is whitespace"
    [ch]
    (when ch
        (or (Character/isWhitespace ^Character ch)
            (identical? \,  ch))))



(defn desugar-meta
    "Resolves syntactical sugar in metadata" ;; could be combined with some other desugar?
    [f]
    (cond
        (keyword? f) {f true}
        (symbol? f)  {:tag f}
        (string? f)  {:tag f}
        :else        f))


(defn reader-error
    "Throws an ExceptionInfo with the given message, and optional maps merged into data-map"
    [msg & maps]
    (println "  ## reader-error msg:" msg)
    (throw (ex-info
               msg
               (apply merge {:type :reader-exception} maps))))


(defn throwing-reader
    [msg]
    (fn [& _]
        (reader-error msg)))


(defn ex-info? [ex]
    (instance? clojure.lang.ExceptionInfo ex))


(definterface IValue (getValue []))

(defn- meta-number
    "A wrapper to set meta on any type"
    ([v] (meta-number {} v))
    ([meta v]
        (let [_meta (atom (merge {:type (type v)} meta))]
            (proxy [java.lang.Number clojure.lang.IObj] []
                    (meta [] @_meta)
                    (withMeta [m] (meta-number (merge {:type (type v)} m) v))
                    (intValue [] (.intValue v))
                    (longValue [] (.longValue v))
                    (floatValue [] (.floatValue v))
                    (doubleValue [] (.doubleValue v))
                    (byteValue [] (.byteValue v))
                    (shortValue [] (.shortValue v))
                    (toString [] (clojure.lang.RT/printString v))
                ))))


(defn- meta-value
    "A wrapper to set meta on any type"
    ([v] (meta-value {} v))
    ([meta v]
        (let [_meta (atom (merge {:type (type v)} meta))]
            (proxy [java.lang.Object clojure.lang.IObj IValue] []
                (meta [] @_meta)
                (withMeta [m] (meta-value (merge {:type (type v)} m) v))
                (getValue [] v)
                (toString [] (clojure.lang.RT/printString v))
                ))))

(defn ^:private ns-name* [x]
    (if (instance? Namespace x)
        (name (ns-name x))
        (name x)))

(defn- macro-terminating? [ch]
    (case ch
        (\" \; \@ \^ \` \~ \( \) \[ \] \{ \} \\) true
        false))


(defn- ^String read-token
    "Read in a single logical token from the reader"
    [rdr initch]
    (if-not initch
        (reader-error rdr "EOF while reading")
        (loop [sb (StringBuilder.) ch initch]
            (if (or (whitespace? ch)
                    (macro-terminating? ch)
                    (nil? ch))
                (do (when ch
                        (unread rdr ch))
                    (str sb))
                (recur (.append sb ch) (read-char rdr))))))



;; don't want this for syntax stuff!
(declare read-tagged)
(defn- read-dispatch
    [rdr _ opts pending-forms]
    (if-let [ch (read-char rdr)]
        (if-let [dm (dispatch-macros ch)]
            (dm rdr ch opts pending-forms)
            (read-tagged (doto rdr (unread ch)) ch opts pending-forms)) ;; ctor reader is implemented as a tagged literal
        (reader-error rdr "EOF while reading character")))

(defn- read-unmatched-delimiter
    [rdr ch opts pending-forms]
    (reader-error rdr "Unmatched delimiter " ch))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; readers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn read-regex
    [rdr ch opts pending-forms]
    (let [sb (StringBuilder.)]
        (loop [ch (read-char rdr)]
            (if (identical? \" ch)
                (Pattern/compile (str sb))
                (if (nil? ch)
                    (reader-error rdr "EOF while reading regex")
                    (do
                        (.append sb ch )
                        (when (identical? \\ ch)
                            (let [ch (read-char rdr)]
                                (if (nil? ch)
                                    (reader-error rdr "EOF while reading regex"))
                                (.append sb ch)))
                        (recur (read-char rdr))))))))

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

(def ^:private ^:const upper-limit (int \uD7ff))
(def ^:private ^:const lower-limit (int \uE000))

(defn- read-char*
    "Read in a character literal"
    [rdr backslash opts pending-forms]
    (let [
            start-location (starting-location rdr)
             ch (read-char rdr)]
        (with-meta (meta-value
                (if-not (nil? ch)
                    (let [token (if (or (macro-terminating? ch)
                                        (whitespace? ch))
                                    (str ch)
                                    (read-token rdr ch))
                          token-len (count token)]
                        (cond

                            (== 1 token-len)  (Character/valueOf (nth token 0))

                            (= token "newline") \newline
                            (= token "space") \space
                            (= token "tab") \tab
                            (= token "backspace") \backspace
                            (= token "formfeed") \formfeed
                            (= token "return") \return

                            (.startsWith token "u")
                            (let [c (read-unicode-char token 1 4 16)
                                  ic (int c)]
                                (if (and (> ic upper-limit)
                                        (< ic lower-limit))
                                    (reader-error rdr "Invalid character constant: \\u" (Integer/toString ic 16))
                                    c))

                            (.startsWith token "o")
                            (let [len (dec token-len)]
                                (if (> len 3)
                                    (reader-error rdr "Invalid octal escape sequence length: " len)
                                    (let [uc (read-unicode-char token 1 len 8)]
                                        (if (> (int uc) 0377)
                                            (reader-error rdr "Octal escape sequence must be in range [0, 377]")
                                            uc))))

                            :else (reader-error "Unsupported character: \\" token)))
                    (reader-error "EOF while reading character")))
                (merge start-location (ending-location rdr))
            )))




(defn- starting-location [rdr]
    {
        :starting-column (dec (.getColumnNumber rdr))
        :starting-line (.getLineNumber rdr)
        :starting-index (dec (.getIndex rdr))
        })

(defn- ending-location [rdr]
    {
        :ending-column (dec (.getColumnNumber rdr))
        :ending-line (.getLineNumber rdr)
        :ending-index (.getIndex rdr)
        })


(defonce ^:private READ_EOF (Object.))
(defonce ^:private READ_FINISHED (Object.))



(def ^:dynamic *read-delim* false)

(defn- ^PersistentVector read-delimited
    "Reads and returns a collection ended with delim"
    [delim rdr opts pending-forms]
    (let [
             ;[start-line start-column] (starting-line-col-info rdr)
            start-location (starting-location rdr)

            delim (char delim)
         ]
        (binding [*read-delim* true]
            (loop [a (transient [])]
                (let [form (read* rdr false READ_EOF delim opts pending-forms)]
                    (if (identical? form READ_FINISHED)
                        (persistent! a)
                        (if (identical? form READ_EOF)
                            (reader-error
                                "EOF while reading" (str ", starting at: " start-location)
                                (merge start-location (ending-location rdr)))
                            (recur (conj! a form)))))))))



(defn- read-and-location [read-fn rdr]
    "Executes the read-fn, and return [result location], where location is a map"
    (let [
            start-info (starting-location rdr)
            result (read-fn)
            end-info (ending-location rdr)
         ]
        [result (merge start-info end-info)]))



(defn- read-list
    "Read in a list, including its location."
    [rdr _ opts pending-forms]
    (let [
            read-fn #(read-delimited \) rdr opts pending-forms)
            [the-list location-info]
                (read-and-location read-fn rdr)
          ]
        (with-meta
            (if (empty? the-list) '() (clojure.lang.PersistentList/create the-list))
            location-info)))


(defn- read-vector
    "Read in a vector, including its location."
    [rdr _ opts pending-forms]
    (let [
            read-fn #(read-delimited \] rdr opts pending-forms)
            [the-vector location-info]
            (read-and-location read-fn rdr)
        ]
        (with-meta the-vector location-info)))


(defn- read-map
    "Read in a map, including its location"
    [rdr _ opts pending-forms]
    (let [
            read-fn #(read-delimited \} rdr opts pending-forms)
            [the-map location-info] (read-and-location read-fn rdr)
            cnt (count the-map)
        ]
        (when (odd? cnt)
            (reader-error rdr "Map literal must contain an even number of forms"))
        (with-meta
            (if (zero? cnt) {} (RT/map (into-array Object the-map)))
            location-info)))


(defn- step-back-start [location]
    "subtract 1 from starting-column and starting-index"
    (update (update location :starting-column dec) :starting-index dec))


(defn- read-set
    "Read in a set, including its location."
    [rdr _ opts pending-forms]
    (let [
            read-fn #(PersistentHashSet/createWithCheck (read-delimited \} rdr opts pending-forms))
            [the-set location-info] (read-and-location read-fn rdr)
            ;; step back start to account for '#' in the leading #{
            location-info (step-back-start location-info)
        ]
        (with-meta the-set location-info)))


(defn- read-number
    [rdr initch]
    (let [
             ;; step back start to account for initch already read
             start-location (starting-location rdr)
         ]
        (loop [sb (doto (StringBuilder.) (.append initch)) ch (read-char rdr)]
            (if (or (whitespace? ch) (macros ch) (nil? ch))
                (let [s (str sb)

                      ]
                    (unread rdr ch)
                    (with-meta
                        (if-let [v (match-number s)]
                            (meta-number v)
                            (reader-error (str "Invalid number format [" s "]")))
                        (merge start-location (ending-location rdr))))
                (recur (doto sb (.append ch)) (read-char rdr))))))





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
            \u (let [ch (read-char rdr)]
                   (if (== -1 (Character/digit (int ch) 16))
                       (reader-error "Invalid unicode escape: \\u" ch)
                       (read-unicode-char rdr ch 16 4 true)))
            (if (numeric? ch)
                (let [ch (read-unicode-char rdr ch 8 3 false)]
                    (if (> (int ch) 0337)
                        (reader-error "Octal escape sequence must be in range [0, 377]")
                        ch))
                (reader-error rdr "Unsupported escape character: \\" ch)))))





(defn- read-string*
    [rdr _ opts pending-forms]
    (let [
             start-location (starting-location rdr)
             ]
        (loop [sb (StringBuilder.)
               ch (read-char rdr)]
            (case ch
                nil (reader-error "EOF while reading string")
                \\ (recur (doto sb (.append (escape-char sb rdr)))
                       (read-char rdr))
                \" (with-meta
                       (meta-value (str sb))
                       (merge start-location (ending-location rdr)))
                (recur (doto sb (.append ch)) (read-char rdr))))))



(defn- read-symbol
    [rdr initch]
    (let [
            ;[line column] (starting-line-col-info rdr)
            start-location (starting-location rdr)
        ]
        (when-let [token (read-token rdr initch)]
            (case token
                ;; special symbols
                "nil" nil
                "true" true
                "false" false
                "/" '/
                "NaN" Double/NaN
                "-Infinity" Double/NEGATIVE_INFINITY
                ("Infinity" "+Infinity") Double/POSITIVE_INFINITY

                (or (when-let [p (parse-symbol token)]
                        (with-meta (symbol (p 0) (p 1))
                            (merge start-location (ending-location rdr))))
                    (reader-error "Invalid token: " token))))))


(def ^:dynamic *alias-map*
    "Map from ns alias to ns, if non-nil, it will be used to resolve read-time
     ns aliases instead of (ns-aliases *ns*).

     Defaults to nil"
    nil)


(defn- resolve-ns [sym]
    (or ((or *alias-map*
             (ns-aliases *ns*)) sym)
        (find-ns sym)))


(defn- read-keyword
    [rdr initch opts pending-forms]
    (let [
             ;; step back start to account for initch already read
             ; start-location (step-back-start (starting-location rdr))
             start-location (starting-location rdr)
             ch (read-char rdr)]
        (with-meta
            (if-not (whitespace? ch)
                (let [token (read-token rdr ch) s (parse-symbol token)]
                    (if s
                        (let [^String ns (s 0)
                              ^String name (s 1)]
                            (if (identical? \: (nth token 0))
                                (if ns
                                    (let [ns (resolve-ns (symbol (subs ns 1)))]
                                        (if ns
                                            (meta-value (keyword (str ns) name))
                                            (reader-error (str "Invalid token: :" token))))
                                    (meta-value (keyword (str *ns*) (subs name 1))))
                                (meta-value (keyword ns name))))
                        (reader-error (str "Invalid token: :" token))))
                (reader-error "Invalid token: :"))
            (merge start-location (ending-location rdr))
        )))


(defn- wrapping-reader
    "Returns a function which wraps a reader in a call to sym"
    [sym]
    (fn [rdr _ opts pending-forms]
        (list sym (read* rdr true nil opts pending-forms))))


(defn- read-meta
    "Read metadata and return the following object with the metadata applied"
    [rdr _ opts pending-forms]
        (let [;[line column] (starting-line-col-info rdr)
              start-location (starting-location rdr)
              m (desugar-meta (read* rdr true nil opts pending-forms))]
            (when-not (map? m)
                (reader-error "Metadata must be Symbol, Keyword, String or Map"))
            (let [o (read* rdr true nil opts pending-forms)]
                (if (instance? IMeta o)
                    (let [m (conj m start-location)]
                        (if (instance? IObj o)
                            (with-meta o (merge (meta o) m))
                            (reset-meta! o m)))
                    (reader-error "Metadata can only be applied to IMetas")))))




(defn- read-discard
    "Read and discard the first object from rdr"
    [rdr _ opts pending-forms]
    (doto rdr
        (read* true nil opts pending-forms)))


(defn read-comment
    [rdr & _]
    (skip-line rdr))



(def ^:private RESERVED_FEATURES #{:else :none})

(defn- has-feature?
    [rdr feature opts]
    (if (keyword? feature)
        (or (= :default feature) (contains? (get opts :features) feature))
        (reader-error (str "Feature should be a keyword: " feature))))


(defn- check-eof-error
    [form rdr first-line]
    (when (identical? form READ_EOF)
        (if (< first-line 0)
            (reader-error "EOF while reading")
            (reader-error "EOF while reading, starting at line " first-line))))


(defn- check-reserved-features
    [rdr form]
    (when (get RESERVED_FEATURES form)
        (reader-error (str "Feature name " form " is reserved"))))


(defn- check-invalid-read-cond
    [form rdr first-line]
    (when (identical? form READ_FINISHED)
        (if (< first-line 0)
            (reader-error "read-cond requires an even number of forms")
            (reader-error (str "read-cond starting on line " first-line " requires an even number of forms")))))


(defn- read-suppress
    "Read next form and suppress. Return nil or READ_FINISHED."
    [first-line rdr opts pending-forms]
    (binding [*suppress-read* true]
        (let [form (read* rdr false READ_EOF \) opts pending-forms)]
            (check-eof-error form rdr first-line)
            (when (identical? form READ_FINISHED)
                READ_FINISHED))))





(def ^:private NO_MATCH (Object.))

(defn- match-feature
    "Read next feature. If matched, read next form and return.
     Otherwise, read and skip next form, returning READ_FINISHED or nil."
    [first-line rdr opts pending-forms]
    (let [feature (read* rdr false READ_EOF \) opts pending-forms)]
        (check-eof-error feature rdr first-line)
        (if (= feature READ_FINISHED)
            READ_FINISHED
            (do
                (check-reserved-features rdr feature)
                (if (has-feature? rdr feature opts)
                    ;; feature matched, read selected form
                    (doto (read* rdr false READ_EOF \) opts pending-forms)
                        (check-eof-error rdr first-line)
                        (check-invalid-read-cond rdr first-line))
                    ;; feature not matched, ignore next form
                    (or (read-suppress first-line rdr opts pending-forms)
                        NO_MATCH))))))

(defn- read-cond-delimited
    [rdr splicing opts pending-forms]
    (let [;first-line (if (indexing-reader? rdr) (get-line-number rdr) -1)
          first-line (.getLineNumber rdr)
          result (loop [matched NO_MATCH
                        finished nil]
                     (cond
                         ;; still looking for match, read feature+form
                         (identical? matched NO_MATCH)
                         (let [match (match-feature first-line rdr opts pending-forms)]
                             (if (identical? match READ_FINISHED)
                                 READ_FINISHED
                                 (recur match nil)))

                         ;; found match, just read and ignore the rest
                         (not (identical? finished READ_FINISHED))
                         (recur matched (read-suppress first-line rdr opts pending-forms))

                         :else
                         matched))]
        (if (identical? result READ_FINISHED)
            rdr
            (if splicing
                (if (instance? List result)
                    (do
                        (.addAll ^List pending-forms 0 ^List result)
                        rdr)
                    (reader-error "Spliced form list in read-cond-splicing must implement java.util.List."))
                result))))


(defn- read-cond
    [rdr _ opts pending-forms]
    (when (not (and opts (#{:allow :preserve} (:read-cond opts))))
        (throw (RuntimeException. "Conditional read not allowed")))
    (if-let [ch (read-char rdr)]
        (let [splicing (= ch \@)
              ch (if splicing (read-char rdr) ch)]
            (when splicing
                (when-not *read-delim*
                    (reader-error "cond-splice not in list")))
            (if-let [ch (if (whitespace? ch) (read-past whitespace? rdr) ch)]
                (if (not= ch \()
                    (throw (RuntimeException. "read-cond body must be a list"))
                    (binding [*suppress-read* (or *suppress-read* (= :preserve (:read-cond opts)))]
                        (if *suppress-read*
                            (reader-conditional (read-list rdr ch opts pending-forms) splicing)
                            (read-cond-delimited rdr splicing opts pending-forms))))
                (reader-error "EOF while reading character")))
        (reader-error "EOF while reading character")))



(def ^:private ^:dynamic arg-env)

(defn- garg
    "Get a symbol for an anonymous ?argument?"
    [n]
    (symbol (str (if (== -1 n) "rest" (str "p" n))
                "__" (RT/nextID) "#")))


(defn- read-fn
    [rdr _ opts pending-forms]
    (if (thread-bound? #'arg-env)
        (throw (IllegalStateException. "Nested #()s are not allowed")))
    (binding [arg-env (sorted-map)]
        (let [form (read* (doto rdr (unread \()) true nil opts pending-forms) ;; this sets bindings
              rargs (rseq arg-env)
              args (if rargs
                       (let [higharg (key (first rargs))]
                           (let [args (loop [i 1 args (transient [])]
                                          (if (> i higharg)
                                              (persistent! args)
                                              (recur (inc i) (conj! args (or (get arg-env i)
                                                                             (garg i))))))
                                 args (if (arg-env -1)
                                          (conj args '& (arg-env -1))
                                          args)]
                               args))
                       [])]
            (list 'fn* args form))))

(defn- register-arg
    "Registers an argument to the arg-env"
    [n]
    (if (thread-bound? #'arg-env)
        (if-let [ret (arg-env n)]
            ret
            (let [g (garg n)]
                (set! arg-env (assoc arg-env n g))
                g))
        (throw (IllegalStateException. "Arg literal not in #()"))))


(declare read-symbol)

(defn- read-arg
    [rdr pct opts pending-forms]
    (if-not (thread-bound? #'arg-env)
        (read-symbol rdr pct)
        (let [ch (peek-char rdr)]
            (cond
                (or (whitespace? ch)
                    (macro-terminating? ch)
                    (nil? ch))
                (register-arg 1)

                (identical? ch \&)
                (do (read-char rdr)
                    (register-arg -1))

                :else
                (let [n (read* rdr true nil opts pending-forms)]
                    (if-not (integer? n)
                        (throw (IllegalStateException. "Arg literal must be %, %& or %integer"))
                        (register-arg n)))))))


(def ^:private ^:dynamic gensym-env nil)

(defn- read-unquote
    [rdr comma opts pending-forms]
    (if-let [ch (peek-char rdr)]
        (if (identical? \@ ch)
            ((wrapping-reader 'clojure.core/unquote-splicing) (doto rdr read-char) \@ opts pending-forms)
            ((wrapping-reader 'clojure.core/unquote) rdr \~ opts pending-forms))))

(declare syntax-quote*)
(defn- unquote-splicing? [form]
    (and (seq? form)
        (= (first form) 'clojure.core/unquote-splicing)))

(defn- unquote? [form]
    (and (seq? form)
        (= (first form) 'clojure.core/unquote)))


(defn- expand-list
    "Expand a list by resolving its syntax quotes and unquotes"
    [s]
    (loop [s (seq s) r (transient [])]
        (if s
            (let [item (first s)
                  ret (conj! r
                          (cond
                              (unquote? item)          (list 'clojure.core/list (second item))
                              (unquote-splicing? item) (second item)
                              :else                    (list 'clojure.core/list (syntax-quote* item))))]
                (recur (next s) ret))
            (seq (persistent! r)))))


(defn- flatten-map
    "Flatten a map into a seq of alternate keys and values"
    [form]
    (loop [s (seq form) key-vals (transient [])]
        (if s
            (let [e (first s)]
                (recur (next s) (-> key-vals
                                    (conj! (key e))
                                    (conj! (val e)))))
            (seq (persistent! key-vals)))))

(defn- register-gensym [sym]
    (if-not gensym-env
        (throw (IllegalStateException. "Gensym literal not in syntax-quote")))
    (or (get gensym-env sym)
        (let [gs (symbol (str (subs (name sym)
                                  0 (dec (count (name sym))))
                             "__" (RT/nextID) "__auto__"))]
            (set! gensym-env (assoc gensym-env sym gs))
            gs)))

(defn ^:dynamic resolve-symbol
    "Resolve a symbol s into its fully qualified namespace version"
    [s]
    (if (pos? (.indexOf (name s) "."))
        s ;; If there is a period, it is interop
        (if-let [ns-str (namespace s)]
            (let [ns (resolve-ns (symbol ns-str))]
                (if (or (nil? ns)
                        (= (ns-name* ns) ns-str)) ;; not an alias
                    s
                    (symbol (ns-name* ns) (name s))))
            (if-let [o ((ns-map *ns*) s)]
                (if (class? o)
                    (symbol (.getName ^Class o))
                    (if (var? o)
                        (symbol (-> ^Var o .ns ns-name*) (-> ^Var o .sym name))))
                (symbol (ns-name* *ns*) (name s))))))


(defn- add-meta [form ret]
    (if (and (instance? IObj form)
            (seq (dissoc (meta form) :line :column :end-line :end-column :file :source)))
        (list 'clojure.core/with-meta ret (syntax-quote* (meta form)))
        ret))

(defn- syntax-quote-coll [type coll]
    ;; We use sequence rather than seq here to fix http://dev.clojure.org/jira/browse/CLJ-1444
    ;; But because of http://dev.clojure.org/jira/browse/CLJ-1586 we still need to call seq on the form
    (let [res (list 'clojure.core/sequence
                  (list 'clojure.core/seq
                      (cons 'clojure.core/concat
                          (expand-list coll))))]
        (if type
            (list 'clojure.core/apply type res)
            res)))


(defn map-func
    "Decide which map type to use, array-map if less than 16 elements"
    [coll]
    (if (>= (count coll) 16)
        'clojure.core/hash-map
        'clojure.core/array-map))


(defn- syntax-quote* [form]
    (->>
        (cond
            (special-symbol? form) (list 'quote form)

            (symbol? form)
            (list 'quote
                (if (namespace form)
                    (let [maybe-class ((ns-map *ns*)
                                          (symbol (namespace form)))]
                        (if (class? maybe-class)
                            (symbol (.getName ^Class maybe-class) (name form))
                            (resolve-symbol form)))
                    (let [sym (name form)]
                        (cond
                            (.endsWith sym "#")
                            (register-gensym form)

                            (.startsWith sym ".")
                            form

                            (.endsWith sym ".")
                            (let [csym (symbol (subs sym 0 (dec (count sym))))]
                                (symbol (.concat (name (resolve-symbol csym)) ".")))
                            :else (resolve-symbol form)))))

            (unquote? form) (second form)
            (unquote-splicing? form) (throw (IllegalStateException. "unquote-splice not in list"))

            (coll? form)
            (cond

                (instance? IRecord form) form
                (map? form) (syntax-quote-coll (map-func form) (flatten-map form))
                (vector? form) (list 'clojure.core/vec (syntax-quote-coll nil form))
                (set? form) (syntax-quote-coll 'clojure.core/hash-set form)
                (or (seq? form) (list? form))
                (let [seq (seq form)]
                    (if seq
                        (syntax-quote-coll nil seq)
                        '(clojure.core/list)))

                :else (throw (UnsupportedOperationException. "Unknown Collection type")))

            (or (keyword? form)
                (number? form)
                (char? form)
                (string? form)
                (nil? form)
                (instance? Boolean form)
                (instance? Pattern form))
            form

            :else (list 'quote form))
        (add-meta form)))


(defn- read-syntax-quote
    [rdr backquote opts pending-forms]
    (binding [gensym-env {}]
        (-> (read* rdr true nil opts pending-forms)
            syntax-quote*)))

(defn- macros [ch]
    (case ch
        \" read-string*
        \: read-keyword
        \; read-comment
        \' (wrapping-reader 'quote)
        \@ (wrapping-reader 'clojure.core/deref)
        \^ read-meta
        \` read-syntax-quote ;;(wrapping-reader 'syntax-quote)
        \~ read-unquote
        \( read-list
        \) read-unmatched-delimiter
        \[ read-vector
        \] read-unmatched-delimiter
        \{ read-map
        \} read-unmatched-delimiter
        \\ read-char*
        \% read-arg
        \# read-dispatch
        nil))


(defn- dispatch-macros [ch]
    (case ch
        \^ read-meta                ;deprecated
        \' (wrapping-reader 'var)
        \( read-fn
;        \= read-eval
        \{ read-set
        \< (throwing-reader "Unreadable form")
        \" read-regex
        \! read-comment
        \_ read-discard
        \? read-cond
        nil))

;; TD: probably don't need this for syntax-stuff only
(comment defn- read-ctor [rdr class-name opts pending-forms]
    (when-not *read-eval*
        (reader-error "Record construction syntax can only be used when *read-eval* == true"))
    (let [class (Class/forName (name class-name) false (RT/baseLoader))
          ch (read-past whitespace? rdr)] ;; differs from clojure
        (if-let [[end-ch form] (case ch
                                   \[ [\] :short]
                                   \{ [\} :extended]
                                   nil)]
            (let [entries (to-array (read-delimited end-ch rdr opts pending-forms))
                  numargs (count entries)
                  all-ctors (.getConstructors class)
                  ctors-num (count all-ctors)]
                (case form
                    :short
                    (loop [i 0]
                        (if (>= i ctors-num)
                            (reader-error "Unexpected number of constructor arguments to " (str class)
                                ": got" numargs)
                            (if (== (count (.getParameterTypes ^Constructor (aget all-ctors i)))
                                    numargs)
                                (Reflector/invokeConstructor class entries)
                                (recur (inc i)))))
                    :extended
                    (let [vals (RT/map entries)]
                        (loop [s (keys vals)]
                            (if s
                                (if-not (keyword? (first s))
                                    (reader-error "Unreadable ctor form: key must be of type clojure.lang.Keyword")
                                    (recur (next s)))))
                        (Reflector/invokeStaticMethod class "create" (object-array [vals])))))
            (reader-error "Invalid reader constructor form"))))



;; probably don't want this either for syntax-stuff
(comment defn- read-tagged [rdr initch opts pending-forms]
    (let [tag (read* rdr true nil opts pending-forms)]
        (if-not (symbol? tag)
            (reader-error "Reader tag must be a symbol"))
        (if *suppress-read*
            (tagged-literal tag (read* rdr true nil opts pending-forms))
            (if-let [f (or (*data-readers* tag)
                           (default-data-readers tag))]
                (f (read* rdr true nil opts pending-forms))
                (if (.contains (name tag) ".")
                    (read-ctor rdr tag opts pending-forms)
                    (if-let [f *default-data-reader-fn*]
                        (f tag (read* rdr true nil opts pending-forms))
                        (reader-error "No reader function for tag " (name tag))))))))






;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; reading
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- normalize-newline [rdr ch]
    (if (identical? \return ch)
        (let [c (peek-char rdr)]
            (when (or (identical? \formfeed c)
                      (identical? \newline c))
                (read-char rdr))
            \newline)
        ch))


(defn- read-char [rdr]
    (let [c (.read rdr)]
        (when (>= c 0)
            (normalize-newline rdr (char c)))))

(defn- unread [rdr c]
    (when c
        (.unread ;^java.io.PushbackReader
            rdr (int c))))

(defn- peek-char [rdr]
    (when-let [c (read-char rdr)]
        (unread rdr c)
        c))


(definterface IIndex
    (^int getIndex []))

(defn create-indexing-linenumbering-pushback-reader [s]
    (let [
             indx (atom 0)
             pusback-reader (java.io.PushbackReader. (java.io.StringReader. s))
             ]
        (proxy [clojure.lang.LineNumberingPushbackReader IIndex] [pusback-reader]
            (read []
                (swap! indx inc)
                (proxy-super read))
            (unread [ch]
                (swap! indx dec)
                (proxy-super unread ch))
            (getIndex []
                @indx)
            )))


(defn ^:private read*
    ;; for initial call
    ([reader eof-error? sentinel opts pending-forms]
        (read* reader eof-error? sentinel nil opts pending-forms))
    ;; for recursive calls
    ([reader eof-error? sentinel return-on opts pending-forms]
        (try
            (loop []
                (if (seq pending-forms)
                    (.remove ^List pending-forms 0)
                    (let [ch (read-char reader)]
                        ;(println "ch:" ch "return-on:" return-on)
                        (cond
                            (whitespace? ch) (recur)
                            (nil? ch) (if eof-error? (reader-error "EOF") sentinel)
                            (= ch return-on) READ_FINISHED
                            (number-literal? reader ch) (read-number reader ch)
                            :else (let [f (macros ch)]
                                      (if f
                                          (let [res (f reader ch opts pending-forms)]
                                              (if (identical? res reader)
                                                  (recur)
                                                  res))
                                          (read-symbol reader ch)))))))
            (catch Exception e
                (if (ex-info? e)
                    (let [d (ex-data e)]
                        (if (= :reader-exception (:type d))
                            (throw e)
                            (throw
                                (ex-info
                                    (.getMessage e)
                                    (merge {:type :reader-exception} d)

                                    e))))
                    (throw
                        (ex-info
                            (.getMessage e)
                            {:type :reader-exception}
                            e)))))))

(defn read-code
    "Reads and parses the string as Clojure source code, returning a sequence of objects tagged with start and end location.
Does not do any form of evaluation."
    [rdr]
    ;(read* (create-indexing-linenumbering-pushback-reader s) false :eof nil {} (LinkedList.))
    (read* rdr false :eof nil {} (LinkedList.))
    ;(read* rdr true nil nil {} (LinkedList.))
    )


(def test-code "(+ 2 3   )(+ 22
  (- 4.4 1/2)
)
{:a [:b \\c #{:d \"A\"}]
   :e \"cool\"}

(defn my-f [s] (println s))
'()
'(8 9)
")



(defn- print-token [t]
    (if (coll? t)
        (do
            (print (type t) ": ")
            (print (map (fn [o] (str o " ")) t)))
        (print (str t)))
        (println "       " (type t) "      " (meta t))
    )

(defn- print-tokens [ts]
        (print-token ts)
        (doseq [t ts]
            (if (coll? t)
                (print-tokens t) ; recur
                (print-token t) )))


(comment let [rdr (create-indexing-linenumbering-pushback-reader test-code)]
    (loop [res nil]
        (when (not= res :eof)
            (if res (print-tokens res))
            (recur (read-code rdr)))))


