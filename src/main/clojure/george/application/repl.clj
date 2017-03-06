(ns george.application.repl
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.tools.nrepl :as repl]
    [clojure.tools.nrepl.server :refer [start-server stop-server]]))


(defonce server_ (atom nil))

(defn stop! []
  (when-let [srvr @server_]
    (stop-server srvr)
    (reset! server_ nil)))


(defn serve!
  "(re)-start nrepl server on optional port default 11000"
  [& [port]]
  (stop!)
  (println "Starting nrepl server...")
  (let [port- (or port 11000)
        srvr (reset! server_ (start-server :port port-))]
       (println srvr)
    srvr))

(def a :AAA)

;; DEV

;(do (println "WARNING! Running george.application.repl/serve!") (serve!))
