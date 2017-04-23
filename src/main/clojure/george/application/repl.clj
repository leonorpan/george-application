(ns george.application.repl
  "This module contains functions for starting and stopping an embedded (nREPL)  server.
  Also utilities for evaluation, handling stacktraces, and more.
  (more documentation needed)
"
  (:use clojure.test)
  (:require
    [clojure.string :as cs]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.nrepl :as nrepl]
    [clj-stacktrace.core :refer [parse-exception]]
    [clj-stacktrace.repl :refer [pst pst-str]]
    [clojure.repl :refer [doc dir]]
    [george.application.repl-server :as repl-server]
    [george.util :as gu]))


;;; session handling

(defonce ^:private default-session_ (atom nil))

(declare session-ensured!)
(declare eval-do)


(defn session-get! []
  @default-session_)


(defn session-close! []
  (when-let [ses @default-session_]
    (try (eval-do :op :close :session ses) (catch Exception e (.printStackTrace e)))
    (reset! default-session_ nil)))


(defn session-create!
  "Create a new session."
  []
  (session-close!)
  (let [new-ses (-> (george.application.repl/eval-do :op :clone) first :new-session)]
    (println "  ## new-ses:" new-ses)
    (reset! default-session_ new-ses)))



(defn session-ensured!
  "Ensures that a (default) session is set, and that there is a server running if optional parameter is truthy."
  [& [serving-ensure?]]
  ;(println "::session-ensured!" serving-ensure?)
  (when serving-ensure? (repl-server/serving-ensure! 0))
  (if-let [ses (session-get!)]
    ses
    (session-create!)))




;;;; evaluation


(defn eval-do
  ;; TODO: documetation needed
  [& {:keys [timeout port serving-ensure? session] :as ops}]
  ;(println "::eval-do ops:" ops)
  (with-open [conn
              (nrepl/connect
                :port  (or port (repl-server/port-get serving-ensure?)))]
    ;(println "  ## conn:" conn)
    (let [m (into {:op :eval}
                  (filter (comp some? val) ops))]
      ;(println "  ## m:" m)
      (-> (nrepl/client conn (or timeout 1000))
          (nrepl/message m)
          doall))))



;;;; utility functions


(defn stacktrace-get
  ([]
   (stacktrace-get (session-get!)))
  ([session]
   (eval-do :op "stacktrace" :session session)))



(defn eval-interrupt
  ;; TODO: documetation needed
  [session eval-id & [timeout]]
  (with-open [conn (nrepl/connect :port (repl-server/port-get))]
    (eval-do :op "interrupt" :session session :interrupt-id eval-id)))
        








(defn message
  "a multi-arity function which build a message based on inputs"
  [& {:as options}]
  (let [msg (conj
              {:op :eval :code "\"Warning: No code to evaluate.\"" :ns "user"}
              options)]

    ;(pprint msg)
    msg))



;(defmacro def-eval
;  [& body]
;  `(with-open [transport# (nrepl/connect :port (repl-server/port-get))]
;     (let [~'transport transport#
;           ~'client (nrepl/client transport# Long/MAX_VALUE)
;           ~'session (nrepl/client-session ~'client)
;           ~'timeout-client (nrepl/client transport# 1000)
;           ~'timeout-session (nrepl/client-session ~'timeout-client)
;           ~'message message
;           ~'simple-eval #(nrepl/message % (message :op :eval :code %2))
;           ~'message-eval #(nrepl/message % %2)
;           ~'simple-values (comp nrepl/response-values ~'simple-eval)]
;       ~@body)))
