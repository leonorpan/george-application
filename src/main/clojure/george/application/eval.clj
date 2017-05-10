;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.eval
  (:require
    [clojure.string :as cs]
    [clj-stacktrace.repl :refer [pst pst-str]]
    [clj-stacktrace.core :refer [parse-exception]]
    [george.application
     [repl :as repl]
     [output :refer [output output-showing?]]]
    [george.javafx :as fx]
    [george.util :as u])
  (:import (javafx.scene.layout GridPane Priority)
           (javafx.scene.control Alert$AlertType Alert)))



(defn- normalize-cause-location [[F L C] source-file]
  [(if (#{(str \" source-file \") "NO_SOURCE_FILE" "null"} F)
     source-file
     F)
   (if (#{"0" nil} L) nil L)
   (if (#{"0" nil} C) nil C)])




(defn- find-cause-location
  "Attempts to locate file:line:column from printout.
   Returns a vector, else nil."
  [ex-str source-file]
  (let [compiling-info (re-find #"compiling:\((.+):(\d+):(\d+)\)" ex-str)
        no-source-file (re-find #"(NO_SOURCE_FILE):(\d+)" ex-str)]
    (cond
      compiling-info
      (normalize-cause-location
        (subvec compiling-info 1)
        source-file)
      no-source-file
      (normalize-cause-location
        (conj (subvec no-source-file 1) nil)
        source-file)
      :default
      nil)))





(defn- exception-dialog [header message details]
  ;; http://code.makery.ch/blog/javafx-dialogs-official/
  (let [textarea
        (doto (fx/textarea :text details :font (fx/SourceCodePro "Regular" 12))
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

    (doto (Alert. Alert$AlertType/ERROR)
      (.setTitle "An error has occoured")
      (.setHeaderText header)
      (.setContentText message)
      (-> .getDialogPane (.setExpandableContent ex-content))
      (.showAndWait))))


(defn- update-type-in-parsed [orig]
  ;(println "/update-orig orig:" orig)
  (try (resolve orig)
       (catch Throwable t (.printStackTrace t))))


(defn- process-error [res source-file]
  ;(println "/process-error res:" res)
  (binding [*out* *err*]
    (let [parsed-ex
          (->
            (repl/eval-do
              :code "(clj-stacktrace.core/parse-exception *e)"
              :session (repl/session-get!))
            first
            :value
            read-string
            (update-in [:class] update-type-in-parsed)
            ((fn [m]
               (if (-> m :cause :class)
                 (update-in m [:cause :class] update-type-in-parsed)
                 m))))
          ex-str (pst-str parsed-ex)
          cause-location (find-cause-location ex-str source-file)]
      (println ex-str)
      (apply printf
             (cons "                file:  %s
                line:  %s
                row:   %s\n"
                   cause-location))

      (when-not (output-showing?)
        (fx/now
          (exception-dialog
            (-> parsed-ex :class .getName)

            (format "%s

    file:    %s
    line:   %s
    col:    %s"
                    (:message parsed-ex)
                    (first cause-location)
                    (if-let [r (second cause-location)] r "unknown")
                    (if-let [c (last cause-location)] c "unknown"))

            ex-str))))
    (println)))



(defn- process-response
  "Returns current-ns. Processes/prints results based on type."
  [res current-ns update-ns-fn]
  ;(pprint res)
  (let [ns (if-let [a-ns (:ns res)] a-ns current-ns)]

    (when (not= ns current-ns)
      (output :ns (u/ensure-newline (str " ns> " ns)))
      (update-ns-fn ns))

    (when-let [s (:value res)]
      (output :res (u/ensure-newline (str " >>> " s))))

    (when-let [st (:status res)]

      (output :system (u/ensure-newline (cs/join " " st))))

    (when-let [o (:out res)]
      (print o) (flush))

    ns))


(defn- indent-input-lines-rest [s]
  (cs/replace s "\n" "\n ... "))


(defn read-eval-print-in-ns
  "returns nil"
  [^String code ^String ns eval-id ^String source-file update-ns-fn]
  (output :in (u/ensure-newline (str " <<< " (indent-input-lines-rest code))))
  (repl/def-eval
    {:code code
     :ns ns
     :session (repl/session-ensured! true)
     :id eval-id
     :line 1
     :column 1
     :file source-file}
    (loop [responses response-seq
           current-ns ns]
      (when-let [response (first responses)]
        (if (= "eval-error" (-> response :status first))
          (do
            (repl/eval-interrupt (:session response) eval-id)
            (process-error response source-file))
          (let [new-ns (process-response response current-ns update-ns-fn)]
            (recur (rest responses) new-ns)))))))


