(ns dev.highlight
    (:require
        [clojure.repl :refer [doc]]
        [clojure.pprint :refer [pp pprint]]
        [clojure.java.io :as cio]

        [clojure.tools.reader :as r]
        [clojure.tools.reader.reader-types :as t]

        [dev.util.java :as j] :reload
        [dev.util.javafx :as fx] :reload
        [dev.util.javafx.classes :as fxc] :reload

        [dev.reader :as my] :reload

        )
    (:import
        [java.util Collections]
        [org.fxmisc.richtext CodeArea LineNumberFactory StyleClassedTextArea StyleSpansBuilder]
        )

    )


(fxc/import!)



(defn- make-code-area []
    (let [
            area
                (doto
                    (StyleClassedTextArea. false)
                    (.setStyle "
                            -fx-font: 14 'Source Code Pro Regular';
                            -fx-padding: 10 5;
                            "))
        ]
        (doto area
            (.setUseInitialStyleForInsertion true)
            (-> .getUndoManager .forgetHistory)
            (-> .getUndoManager .mark)
            (.selectRange 0 0))))





(defn- read-sample-code[]
    (slurp (cio/resource "dev/highlight/sample_code.clj")))



(defn- handle-exception [code-area exception]
    (let [ m (.getMessage exception)
           d (.getData exception)
           l (:line d)
           c (:column d)
           pos0 (.position code-area 1 1)
            pos (.position code-area l c)
            offset (.toOffset pos)
             ]
        ;(.append code-area (.subView code-area pos0 pos)
        (prn "message:" m)
        (prn "   data:" d)
        (prn "    pos:" pos)
        (prn " offset:" offset)

        )
    )


;(def LR (clojure.lang.LispReader.))
;(-> LR class (.getDeclaredField "macros") (doto (.setAccessible true)) (.get LR) (get (int \;)) println)
;(-> LR class (.getDeclaredMethod "readNumber" (j/vargs-1 Class java.io.PushbackReader Character/TYPE)) (doto (.setAccessible true)) println)

;(-> LR class (.getDeclaredMethod "readNumber" (j/vargs-1 Class java.io.PushbackReader Character/TYPE)) (doto (.setAccessible true))
;    (.invoke nil (j/vargs-1 Object (java.io.PushbackReader.(java.io.StringReader. "2")) \a)))




(defn- style-class [area  length css-class ranges]
    (doseq [[from to] ranges]
        (let [

                 to (if (> to length) length to)]
            (println "style-class:" css-class from to)
            (.setStyleClass area from to css-class))))


(defn- ranges [typ from to]
    (cond
        (isa? typ clojure.lang.IPersistentSet) [[from (+ 2 from)] [(dec to) to]]
        (isa? typ java.lang.Iterable) [[from (inc from)] [(dec to) to]]
        :else [[from to]]
    ))

(defn- type->css-class [typ]
    (println "type->css-class")
    (println "  ## typ:" typ)
    (cond
        (isa? typ java.lang.Number)  "teal"
        (#{String Character} typ) "green"
        (isa? typ java.lang.Iterable)   "blue"
        (= typ clojure.lang.Keyword)  "pink"
        (#{clojure.lang.Symbol}  typ) "black"
        :else "orange"
    ))


(defn- highlight-it [token code-area length]
    (println "highlight-it")
    (println "  ## token:" token)
    (when-let [m (meta token)]
        (println "  ## m:" m)
        (let [
                 {from :starting-index to :ending-index typ :type} m
                 typ (if typ typ (type token))
                 c (type->css-class typ)
             ]
            (style-class code-area length c (ranges typ from to ) ))))



(defn- highlight-them [tokens code-area length]
    (println "highlight-them")
    (println "  ## tokens:" tokens)
    (highlight-it tokens code-area length)
    (when (coll? tokens)
        (doseq [token tokens]
            (highlight-them token code-area length))))



(defn- highlight-code-2 [code-area old-code code]
    "a second attempt at a real reader/higlighter"
    (if (not= old-code code)
        (let [
                 rdr (my/create-indexing-linenumbering-pushback-reader code)
                 length (. code length)
                 ]
            (loop [res nil]
                (when (not= res :eof)
                    (let [
                             res (try
                                     (my/read-code rdr)

                                     (catch Exception ex
                                         ;(handle-exception code-area ex)
                                         (println "ex:" ex)
                                         (t/read-char rdr)  ;; TODO: fix this hack!

                                         ))
                             ]
                        ; (prn "res:" res "  meta:" (meta res))
                        (highlight-them res code-area length)

                        ;(println res "  type:" (type res) "  seq?:" (seq? res) "  line:" (t/get-line-number reader) "  column:" (t/get-column-number reader))
                        ;(println res "  type:" (type res) "  seq?:" (seq? res) "  line:" (.getLineNumber reader) "  column:" (.getColumnNumber reader)  "  index:" (.getIndex reader))
                        ;(when (seq? res)(doseq [r res] (println r "  type:" (type r))))

                        (recur res)))))))

(defn- code-area-change-listener [code-area]
    (reify ChangeListener
        (changed [_ obs old-txt new-txt]
            (highlight-code-2 code-area old-txt new-txt))))


(defn- build-scene []
    (let [
            area (make-code-area) ;(CodeArea.)
            linenumber-factory (LineNumberFactory/get area)
            _ (.setParagraphGraphicFactory area linenumber-factory)

            sample-code (read-sample-code)
            _ (.replaceText area 0 0 sample-code)

            scene (Scene. (StackPane. (j/vargs area)) 600 400)

            ]
        (fx/add-stylesheets scene "styles/basic.css" "dev/highlight/code.css" )

        (-> area .textProperty (.addListener (code-area-change-listener area)))
        (highlight-code-2 area nil (. area getText))

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