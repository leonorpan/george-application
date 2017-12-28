; Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns george.application.repl
  "This module contains functions for starting and stopping an embedded (nREPL)  server.
  Also utilities for evaluation .
  (More documentation needed)"
  (:require
    [clojure
     [pprint :refer [pprint]]
     [repl :refer [doc dir]]]
    [clojure.tools.nrepl :as nrepl]
    [george.application.repl-server :as repl-server])
  (:import
    [java.net SocketException]))


;;; session handling

(declare
  session-ensure!
  eval-do
  ping)


(defonce ^:private default-session_ (atom nil))


(defn session [& [not-found]]
  (or @default-session_ not-found))


(defn session? []
  (boolean (and (session) (ping))))


(defn session-close! []
  (when-let [ses @default-session_]
    (try (eval-do :op :close :session ses :timeout 1000) (catch Exception e (.printStackTrace e)))
    (reset! default-session_ nil)))


(defn session-create!
  "Create a new session."
  []
  (session-close!)
  (let [new-ses
        (-> (george.application.repl/eval-do :op :clone)
            first
            :new-session)]

    (reset! default-session_ new-ses)))


(defn session-ensure!
  "Ensures that a (default) session is set, and that there is a server running if optional parameter is truth-y."
  [& [serve-ensure?]]
  (when serve-ensure? (repl-server/serve-ensure! 0))
  (if-let [ses (session)]
    ses
    (session-create!)))


;;;; evaluation


(defn eval-do
  ;; TODO: documentation needed
  [& {:keys [timeout port serve-ensure? session] :as ops}]
  (when serve-ensure? (repl-server/serve-ensure! 0))
  (with-open [conn (nrepl/connect :port (or port (repl-server/port)))]
    (let [m (into {:op :eval} (filter (comp some? val) ops))]
      ;; MAX_VALUE default to prevent timeout if code does `Thread/sleep`
      (-> (nrepl/client conn (or timeout Integer/MAX_VALUE))
          (nrepl/message m)
          doall))))


(defmacro def-eval
  ;; TODO: documentation needed
  [ops & body]
  `(let [{:keys [timeout# port# serve-ensure?#] :as ops#} ~ops]
     (when serve-ensure?# (repl-server/serve-ensure! 0))
     (with-open [conn# (nrepl/connect :port (or port# (repl-server/port)))]
       (let [m#
             (into {:op :eval} (filter (comp some? val) ops#))
             ~'response-seq
             ;; MAX_VALUE default to prevent timeout if code does `Thread/sleep`
             (-> (nrepl/client conn# (or timeout# Integer/MAX_VALUE))
                 (nrepl/message m#))]
         ~@body))))


(defn interrupted?
  "Returns true if not the session was interrupted.'"
  [status]
  (not ((set status) "session-idle")))


(defn interrupt-eval
  ([eval-id]
   (interrupt-eval (session) eval-id))
  ([session eval-id]
   (eval-do :op :interrupt :session session :interrupt-id eval-id :timeout 1000)))


(defn interrupt
  "Returns true if the session was not already idle."
 ([]
  (interrupt (session)))
 ([^String session]
  (-> (eval-do :op :interrupt :session session :timeout 1000)
      first
      :status
      interrupted?)))


(defn sessions
  "Returns vector of all session ids as strings"
  []
  (->
    (eval-do :op :ls-sessions)
    first
    :sessions))


(defn ping
  ([]
   (ping (session "NO_DEFAULT_SESSION")))
  ([session-id]
   (try
     (let [res
           (eval-do :op :eval :code ":ping" :session session-id :timeout 1000)]
       (-> res
           nrepl/response-values
           first
           (= :ping)))
     (catch SocketException _ false))))