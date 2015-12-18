(ns dev.highlight
    (:require
        [clojure.repl :refer [doc]]
        [clojure.java.io :as cio]
        [dev.util.java :as j] :reload
        [dev.util.javafx :as fx] :reload
        [dev.util.javafx.classes :as fxc] :reload
    )
    (:import

        [org.fxmisc.richtext CodeArea LineNumberFactory]
    )

    )


(fxc/import!)







(defn- read-sample-code[]
    (slurp (cio/resource "dev/highlight/sample_code.clj")))


(defn- build-scene []
    (let [
            code-area (CodeArea.)
            linenumber-factory (LineNumberFactory/get code-area)
            _ (.setParagraphGraphicFactory code-area linenumber-factory)

            sample-code (read-sample-code)
            _ (.replaceText code-area 0 0 sample-code)

        ]
        (Scene. (StackPane. (j/vargs code-area)) 600 400)))


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