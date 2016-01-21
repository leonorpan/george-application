(ns george.input
    (:import [clojure.lang LineNumberingPushbackReader]
             [java.io StringReader])

    (require
        [clojure.string :as s]
        [george.output :as output] :reload

        )
    (import
        [java.io StringReader]
        [clojure.lang LineNumberingPushbackReader]

        )
    )

(defn- with-newline [obj]
    "ensures that the txt ends with a new-line"
    (let [txt (if (nil? obj) "nil" (str obj))]
        (if (s/ends-with? txt "\n")
            txt
            (str txt "\n"))))


(defn read-eval-print [code]
    (let [
             rdr (LineNumberingPushbackReader. (StringReader. code))
         ]
        (loop []
                (let [
                         form (try (read rdr false :eof)
                                  (catch Exception e
                                      (output/output :err (with-newline e))
                                      :ex))
                                  ]
                    (when-not (= form :eof)
                        (if-not (= form :ex)
                            (output/output :res (with-newline (eval form))))
                        (recur))))))


(defn run [code]
    (output/output :in (with-newline code))
    (read-eval-print code)
    :done)


;(run "(println \"(+ 2 3)\"))\n(+ 4 (+ 2 3))")
(run "(println (+ 2 3)))\n((+ 4 5)")