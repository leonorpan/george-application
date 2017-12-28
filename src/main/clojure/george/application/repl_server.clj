; Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns george.application.repl-server
  "This module contains functions for starting and stopping an embedded (nREPL)  server.
  Also utilities for evaluation, handling stacktraces, and more.
  (more documentation needed)
"
  (:require
    [clojure
     [pprint :refer [pprint]]
     [repl :refer [doc dir]]]
    [clojure.tools.nrepl.server :refer [start-server stop-server]]
    [clojure.tools.nrepl.middleware.session :refer [session]]
    [cider.nrepl :refer [cider-nrepl-handler]]
    [george.util :refer [pprint-str]]
    [clojure.tools.nrepl :as nrepl]))


(defonce ^:private server_ (atom nil))


(defn server []
  @server_)


(defn port []
  (:port (server)))


(defn ping []
  (try
    (with-open [conn (nrepl/connect :port (port))]
        (-> (nrepl/client conn 1000)
            (nrepl/message {:op :eval :code ":ping"})
            nrepl/response-values
            first
            (= :ping)))
    (catch AssertionError e  ;; AssertionError Assert failed: port  clojure.tools.nrepl/connect (nrepl.clj:174)
      false)))


(defn serving? []
  (boolean (and (server) (ping))))


(defn stop! []
  "stops running nrepl-server"
  (when-let [srvr @server_]
    (stop-server srvr)
    (reset! server_ nil)))


(defn serve!
  "(re)-start nrepl server on optional port default 11000.
  If passed-in port is 0, then a free port will be auto-selected."
  [& [port]]
  (stop!)
  (let [prt (or port 11000)]
    (reset! server_
            (start-server :port prt :handler cider-nrepl-handler))))



(defn serve-ensure!
  "Use this function in stead of 'serve!' to avoid re-starting a running server."
  [& [port]]
  (if-not (serving?)
    (serve! port)
    @server_))

;; TODO: Authentication, to allow others to remotely connect to your instance?
;; For now, server binds to localhost/loopback by default, so no access from other machines.
