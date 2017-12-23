;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.eval
  (:require
    [clojure.string :as cs]
    [clojure.pprint :refer [pprint]]
    [clj-stacktrace.repl :refer [pst pst-str]]
    [clj-stacktrace.core :refer [parse-exception]]
    [george.application
     [repl :as repl]
     [output :refer [sprint sprintln output-showing?]]]
    [george.javafx :as fx]
    [george.util.text :as ut])
  (:import (javafx.scene.layout GridPane Priority)
           (javafx.scene.control Alert$AlertType Alert TextArea)
           (clojure.lang LineNumberingPushbackReader)))


;(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defn- exception-dialog [header message details]
  ;; http://code.makery.ch/blog/javafx-dialogs-official/
  (let [textarea
        (doto ^TextArea (fx/textarea :text details :font (fx/SourceCodePro "Regular" 12))
          (.setEditable false)
          (.setWrapText false)
          (.setMaxWidth Double/MAX_VALUE)
          (.setMaxHeight Double/MAX_VALUE)
          (GridPane/setVgrow Priority/ALWAYS)
          (GridPane/setHgrow Priority/ALWAYS))
        label
        (fx/label "The exception stacktrace is:")
        ex-content
        (doto (GridPane.)
          (.setMaxWidth Double/MAX_VALUE)
          (.setPrefWidth 800)
          (.add label 0 0)
          (.add textarea 0 1))]

    (doto (Alert. Alert$AlertType/INFORMATION)
      (.setTitle "An error occurred")
      (.setHeaderText header)
      (.setContentText message)
      (-> .getDialogPane (.setExpandableContent ex-content))
      (.showAndWait))))


(defn- update-type-in-parsed [orig]
  ;(println "/update-orig orig:" orig)
  (try (resolve orig)
       (catch Throwable t orig)))
       ;(catch Throwable t (.printStackTrace t))))


(defn- process-error [e F L C]
  (binding [*out* *err*]
    (let [raw-exception-data
          (if e
            (parse-exception e)
            (->
              (repl/eval-do
                :code "(clj-stacktrace.core/parse-exception *e)"
                :session (repl/session-get!))
              first
              :value
              read-string))

          exception-data
          (->
            raw-exception-data
            (update-in [:class] update-type-in-parsed)
            ((fn [m]
               (if (-> m :cause :class)
                 (update-in m [:cause :class] update-type-in-parsed)
                 m))))

          exception-str
          (format "%s:\n    %s" (.getName ^Class (:class exception-data)) (:message exception-data))
          stacktrace-str
          (pst-str exception-data)
          caused-by-str
          (if-let [cause (:cause exception-data)]
            (format "Caused by:\n    %s %s\n" (:class cause) (:message cause))
            "")
          location-str (format "In file:\n    %s\nStarting at:\n    row: %s  column: %s" F L C)]

      ;; print to Output
      ;(pprint exception-data)
      (println stacktrace-str)
      (println "\nAn error occurred")
      (println "=================")
      (println (format "%s\n\n%s\n%s\n" exception-str caused-by-str location-str))

      ;; maybe show dialog
      (when-not (output-showing?)
        (fx/now
          (exception-dialog
            ;; header
            (.getName ^Class (:class exception-data))
            ;; info-area
            (format "%s\n\n%s\n%s\n" (:message exception-data) caused-by-str location-str)
            ;; details
            stacktrace-str))))
    (println)))


(defn- process-response
  "Returns current-ns. Processes/prints results based on type."
  [res current-ns update-ns-fn]
  ;(pprint res)
  (let [ns (if-let [a-ns (:ns res)] a-ns current-ns)]

    (when (not= ns current-ns)
      (sprint :ns (ut/ensure-newline (str " ns> " ns)))
      (update-ns-fn ns))

    (when-let [s (:value res)]
      (sprint :res (ut/ensure-newline (str " >>> " s))))

    (when-let [st (:status res)]

      (sprint :system (ut/ensure-newline (cs/join " " st))))

    (when-let [o (:out res)]
      (print o) (flush))

    ns))


(defn- indent-input-lines-rest [s]
  (cs/replace s "\n" "\n ... "))


(def whitespace #{\tab \space \newline})


(defn- consume-leading-whitespace [^LineNumberingPushbackReader rdr]
  (loop [ch (.read rdr)]
    (when (not= ch -1)
      (if (whitespace (char ch))
        (recur (.read rdr))
        (.unread rdr ch)))))


(defn- line-column-read [F ^LineNumberingPushbackReader rdr]
  (consume-leading-whitespace rdr)
  (let [lin (.getLineNumber rdr)
        col (.getColumnNumber rdr)]
    (try
      [lin col (read rdr false nil)]
      (catch Throwable e
        (process-error e F lin col)))))


(defn read-eval-print-in-ns
  "returns nil"
  [^String code ^String ns eval-id ^String file-name update-ns-fn]
  (sprint :in (ut/ensure-newline (str " <<< " (indent-input-lines-rest code))))
  (let [rdr (george.code.tokenizer/indexing-pushback-stringreader code)]
    (loop [[lin col rd :as LCR] (line-column-read file-name rdr)]
      ;(prn "  ## LCR:" LCR)
      (when rd
        (let [ok?
              (repl/def-eval
                {:code (str rd)
                 :ns ns
                 :session (repl/session-ensured! true)
                 :id eval-id
                 :line lin
                 :column col
                 :file file-name}
                (loop [responses response-seq
                       current-ns ns]
                   (if-let [response (first responses)]
                          (let [error? (= "eval-error" (-> response :status first))]
                            (if-not error?
                              (let [new-ns (process-response response current-ns update-ns-fn)]
                                (recur (rest responses) new-ns))
                              (do
                                (repl/eval-interrupt (:session response) eval-id)
                                (process-error nil file-name lin col)
                                false))) ;; it did not go OK.  :-(
                          true)))] ;; Everything went OK  :-)
            (when ok?
              (recur (line-column-read file-name rdr))))))))
