(ns george.output

    (:require
        [clojure.repl :refer [doc]]
        [george.output-stage :as gos] :reload

              )

    (:import

        [java.io StringWriter OutputStreamWriter PrintStream]
        [org.apache.commons.io.output WriterOutputStream]
        )
    )






(defn output-string-writer [typ] ;; type is one of :out :err
    (proxy [StringWriter] []
        (flush []
            ;; first print the content of StringWriter to output-stage
            (let [s (str this)]
                ;(gos/out s)
                (gos/output typ s))
            ;; then flush the buffer of the StringWriter
            (let [sb (. this getBuffer)]
                (. sb delete 0 (. sb length))))))


(defonce standard-out System/out)
(defonce standard-err System/err)


(defn wrap-outs []
    (gos/show-output-stage)
    (let [
            ow (output-string-writer :out)
            ew (output-string-writer :err)
        ]
        (System/setOut (PrintStream. (WriterOutputStream. ow) true))
        (System/setErr (PrintStream. (WriterOutputStream. ew) true))

        (alter-var-root #'*out* (constantly ow))
        (alter-var-root #'*err* (constantly ew))
        (println "outs set to output outs" )))



(defn unwrap-outs []
    (System/setOut standard-out)
    (System/setErr standard-err)
    (alter-var-root #'*out* (constantly (OutputStreamWriter. System/out)))
    (alter-var-root #'*err* (constantly (OutputStreamWriter. System/err)))
    (println "outs set to standard outs"))




(defn start-output-stage
    []
    (gos/show-output-stage)
    (wrap-outs))


(defn end-output-stage
    []
    (unwrap-outs)
    (gos/close-output-stage))


(defn output [typ txt] ;; type is one of :in :res :out :err
    (if (gos/output-stage-showing?)
        (gos/output typ txt)
        (println txt)
    ))


nil