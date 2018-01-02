;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.core.history
  (:require [clojure.java.io :as cio]
            [clojure.edn :as edn]

            [george.javafx.java :as j]
            [george.util :as u]
            [george.editor.core :as ed])

  (:import (george.application Versions)
           (java.util Date UUID)
           (java.sql Timestamp)))


(def HISTORY_FILE (cio/file Versions/APPDATA_DIR "repl" "history.edn"))
(.mkdirs (.getParentFile HISTORY_FILE))
;(println "HISTORY_FILE:" HISTORY_FILE)


(defonce ^:private history-atom (atom []))

(defonce ^:private repls-nr-atom (atom 0))

(defn next-repl-nr [] (swap! repls-nr-atom inc))

(defn uuid [] (str (UUID/randomUUID)))


(defn- load-history []
  (when (.exists HISTORY_FILE)
    (binding [*data-readers* {'inst clojure.instant/read-instant-timestamp}]
      ;(println "reading history from file ...")
      (reset! history-atom (edn/read-string (slurp HISTORY_FILE))))))
      ;(println " ... done")

(load-history)


(defn- prune [vec max]
  "returns vector containing last 'max' of 'vec'"
  (let [
        len (count vec)
        i (if (> len max)  (- len max) 0)]
    (subvec vec i)))


(defn append-history [repl-uuid content]
  (let [item {:repl-uuid repl-uuid
              :timestamp (Timestamp. (.getTime (Date.)))
              :content   content}]
        ;_ (println "item:" item)


    (swap! history-atom #(-> % (prune 100) (conj item)))
    (j/thread
      ;(println "writing history to file ...")
      (spit HISTORY_FILE (pr-str @history-atom)))))
      ;(println " ... done")




(def NEXT 1)
(def PREV -1)

(defn do-history [code-area repl-uuid current-history-index-atom direction global?]
  (let [
        items-global
        (reverse @history-atom)

        items
        (if global?
          items-global
          (filter #(= (:repl-uuid %) repl-uuid) items-global))

        i (+ @current-history-index-atom (- direction))
        i (if (< i -1) -1 i)
        i (if (> i (count items)) (count items) i)
        content
        (if (and
                (not (empty? items))
                (not (< i 0))
                (not (>= i (count items))))

            (:content (nth items i)))

        content
        (if content
          content
          (if (< i 0)
            ""
            (if (= (count items) (count items-global))
              ";; No more global history."
              ";; No more local history.\n;; To access global history use SHIFT-CLICK.")))]


    (reset! current-history-index-atom i)
    (doto code-area
      (ed/set-text content))))


