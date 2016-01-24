(ns george.input
    (:import [clojure.lang LineNumberingPushbackReader]
             [java.io StringReader])

    (require
        [clojure.string :as s]
        [george.java :as j] :reload
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
    (output/output :in (with-newline code))

    (j/thread (let [rdr (LineNumberingPushbackReader. (StringReader. code))]
        (loop []
                (let [
                         form (try (read rdr false :eof)
                                  (catch Exception e
                                      (Thread/sleep 50) ;; give output time to print :in before printing ;err
                                      (output/output :err (with-newline e))
                                      (. output/standard-err println e)
                                      :ex))
                                  ]
                    (when-not (= form :eof)
                        (if-not (= form :ex)
                            ;; TODO: handle namespaces
                            ;; TODO: ensure eval-errors are connected to input, not application code!
                            (output/output :res (with-newline (eval form))))
                        (recur)))))))


(defn run [code]
    (read-eval-print code)
    nil)


;(run "(println \"(+ 2 3)\"))\n(+ 4 (+ 2 3))")
;(run "(println (+ 2 3)))\n((+ 4 5)")

nil