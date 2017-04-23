; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.application.repl-server
  "This module contains functions for starting and stopping an embedded (nREPL)  server.
  Also utilities for evaluation, handling stacktraces, and more.
  (more documentation needed)
"
  (:require
    [clojure.string :as cs]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.nrepl :as nrepl]
    [clojure.tools.nrepl.server :refer [start-server stop-server] :as nserver]
    [clojure.tools.nrepl.transport :as ntransport]
    [clojure.tools.nrepl.misc :refer [uuid] :as nmisc]
    [clojure.tools.nrepl.middleware.session :refer [session]]
    [cider.nrepl :refer [cider-nrepl-handler]]
    [clj-stacktrace.core :refer [parse-exception]]
    [clj-stacktrace.repl :refer [pst pst-str]]
    [clojure.repl :refer [doc dir]]
    [george.util :refer [pprint-str]])
  (:import (clojure.tools.nrepl.transport Transport)))








(defn current-time-request-handler
  "nREPL server middleware.
  Returns a function which wraps the given handler.
  This is a trivial example.
  Returns current time in millis :op is \"time?\" in slot ':time'."
  [handler]
  (fn [{:keys [op transport] :as msg}]
    (if (= op "time?")
      (ntransport/send transport (nmisc/response-for msg :status :done :time (System/currentTimeMillis)))
      (handler msg))))



;;;; Exception/stacktrace stuff

(defn stacktrace-parsed []
  (parse-exception *e))

(defn stacktrace-print []
  (pst *e))

(defn stacktrace-str []
  (pst-str *e))


(defn- get-stacktrace [])




(defn wrap-stacktrace-reply
  [{:keys [session transport] :as msg}]
  (pprint msg)
  (if-let [e (@session #'*e)]
    (ntransport/send transport (nmisc/response-for msg (assoc msg :hello "again")))
    (ntransport/send transport (nmisc/response-for msg :status :no-error)))
  (ntransport/send transport (nmisc/response-for msg :status :done)))


(defn wrap-stacktrace
  "Middleware that handles stacktrace requests, sending cause and stack frame
  info for the most recent exception."
  [handler]
  (fn [{:keys [op] :as msg}]
    (case op
      "stacktrace" (wrap-stacktrace-reply msg)
      (handler msg))))




(defn-  exception-response-handler
  "nREPL server middleware.
  Returns a function which wraps the given handler.
  This handler looks at the returning response, and if in contains an exception, inserts the stacktrace."
  [handler]
  (fn [{:keys [^Transport transport] :as msg}]
    (handler
      (assoc msg
        :transport
        (reify Transport
          (recv [_] (.recv transport))
          (recv [_ timeout] (.recv transport timeout))
          (send [_ {:keys [session ex root-ex] :as resp}]
            ;(pprint [" ## resp:" resp])
            (.send transport
              (if (or ex root-ex)
                (assoc resp :stacktrace-str (@session #'*e)) ;(parse-exception *e))
                resp))))))))


(declare port-get)

(defonce ^:private server_ (atom nil))



(defn stop! []
  "stops running nrepl-server"
  (when-let [srvr @server_]
    (stop-server srvr)
    (reset! server_ nil)))


(defn serve!
  "(re)-start nrepl server on optional port default 11000.
  If passed-in port is 0, then a free port will be automselected."
  [& [port]]
  (stop!)
  (println "Starting nrepl server...")
  (let [port- (or port 11000)
        srvr (reset! server_
                     (start-server :port port-
                                   :handler cider-nrepl-handler))]
                                   ;(-> (nserver/default-handler)
                                   ;    (current-time-request-handler)
                                   ;    (wrap-stacktrace))))];exception-response-handler))))]

    (println srvr
       (println "nREPL server started on port" (:port srvr)))
    srvr))
;; TODO: Authentication, to allow others to remotely connect to your instance?
;; For now, server binds to localhost/loopback by default, so no access from other machines.



(defn serving-ensure!
  "Use this function in stead of 'serve!' to avoid re-starting a running server."
  [& args]
  (when-not (port-get)
    (apply serve! args)))


(defn port-get
  "server port of running nrepl-server, else nil. If optional argument true, then ensures server is running."
  [& [serving-ensure?]]
  (when serving-ensure? (serving-ensure!))
  (:port @server_))




