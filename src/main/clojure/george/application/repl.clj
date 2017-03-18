(ns george.application.repl
  "This module contains functions for starting and stopping an embedded (nREPL)  server.
  Also utilities for evaluation, handling stacktraces, and more.
  (more documentation needed)
"
  (:use clojure.test)
  (:require
    [clojure.string :as cs]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.nrepl :as repl]
    [clojure.tools.nrepl.server :refer [start-server stop-server]]
    [clj-stacktrace.core :refer [parse-exception]]
    [clj-stacktrace.repl :refer [pst pst-str]]
    [clojure.repl :refer [doc dir]]
    [clojure.tools.nrepl.server :as server]))



;;;; server stuff

(defonce ^:private server_ (atom nil))


(defn port
  "server port of running nrepl-server, else nil"
  []
  (:port @server_))


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
        srvr (reset! server_ (start-server :port port-))] ; :handler (server/default-handler)))]
       ;(println srvr)
       (println "nREPL server started on port" (port))
    srvr))
;; TODO: Authentication, to allow others to remotely connect to your instance?
;; For now, server binds to localhost/loopback by default, so no access from other machines.


(defn ensure-serve!
  "We don't want (or need) to restart the server.
  Use this function in stead of 'serve!'"
  [& args]
  (when-not (port)
    (apply serve! args)))

;;;; Exception/stacktrace stuff

(defn stacktrace []
  (parse-exception *e))

(defn print-stacktrace []
  (pst *e))

(defn stacktrace-str []
  (pst-str *e))


;;;; Evaluation stuff


(defn uuid
  "Returns a new UUID string."
  []
  (str (java.util.UUID/randomUUID)))


(defn do-eval
  ;; TODO: documetation needed
  [session-id eval-id ns-str code-str] ;& [timeout]]
  (with-open [conn (repl/connect :port (port))]
    (-> (repl/client conn (or  1000))
        (repl/message {:op "eval"
                       :session session-id :id eval-id
                       :ns ns-str :code code-str})

        doall)))


(defn interrupt-eval
  ;; TODO: documetation needed
  [session-id eval-id & [timeout]]
  (with-open [conn (repl/connect :port (port))]
    (-> (repl/client conn (or timeout 5000))
        (repl/message {:op "interrupt" :session session-id :interrupt-id eval-id})
        doall)))


;;;; test stuff


(defn test-eval-interrupt []
  (let [

        code (repl/code (dotimes [i 5] (println "i:" i "...") (Thread/sleep 2000)))
        code (repl/code (time (reduce + (range 1e6))))
        code (repl/code (Thread/sleep 500)
                        (+ 1 2)
                        (println "printing something (to out)")
                        (Thread/sleep 1000)
                        (+ 2 3)
                        (Thread/sleep 1000)
                        (+ 3 5))
        code (repl/code (println "Hello!"))
        code (repl/code *out*)
        id (uuid)

        conn (repl/connect :port (port))
        client (repl/client conn 1000)
        session (repl/new-session client)
        client-session (repl/client-session client :session session)
        eval-message {:op "eval" :code code :ns "user"} ;:id id}
        interrupt-message {:op "interrupt" :session session} ;:interrupt-id id}

        _ (.start (Thread. #(let [res1 (repl/message client-session eval-message)]
                              (pprint ["res1.0" (doall res1)])
                              (pprint ["res1.1" (repl/combine-responses res1)]))))


        _ (.start (Thread. #(let [_ (Thread/sleep 1000)
                                  res2 (repl/message client-session interrupt-message)]
                              (pprint ["res2" res2]))))]))

;(serve!)
;(test-eval-interrupt)









;(def ^:dynamic *repl-out* *out*)




;(defmacro def-repl-test
;  [name & body]
;  `(deftest ~(with-meta name {:private true})
;            (with-open [transport# (repl/connect :port (port))]
;              (let [~'transport transport#
;                    ~'client (repl/client transport# Long/MAX_VALUE)
;                    ~'session (repl/client-session ~'client)
;                    ~'timeout-client (repl/client transport# 1000)
;                    ~'timeout-session (repl/client-session ~'timeout-client)
;                    ~'repl-eval #(repl/message % {:op :eval :code %2})
;                    ~'repl-values (comp repl/response-values ~'repl-eval)]
;                ~@body))))


;(def ^{:dynamic true} *server* nil)
;(def ^{:dynamic true} *out* nil)

(defn message
  "a multi-arity function which build a message based on inputs"
  [& {:as options}]
  (let [msg (conj
              {:op :eval :code "\"Warning: No code to evaluate.\"" :ns "user"}
              options)]

    ;(pprint msg)
    msg))



(defmacro def-eval
  [& body]
  `(with-open [transport# (repl/connect :port (port))]
     (let [~'transport transport#
           ~'client (repl/client transport# Long/MAX_VALUE)
           ~'session (repl/client-session ~'client)
           ~'timeout-client (repl/client transport# 1000)
           ~'timeout-session (repl/client-session ~'timeout-client)
           ~'message message
           ~'simple-eval #(repl/message % (message :op :eval :code %2))
           ~'message-eval #(repl/message % %2)
           ~'simple-values (comp repl/response-values ~'simple-eval)]
       ~@body)))


;(def-repl-test sessionless-*out*
;               (is (= "5\n:foo\n"
;                      (-> (repl-eval client "(println 5)(println :foo)")
;                          repl/combine-responses
;                          :out))))

;(ensure-serve!)

;(pprint (macroexpand-1 '(do-eval (-> (repl-eval session "(println 5)(println :foo)") repl/combine-responses :out))))

;(println)
;(println "*out0*:" *repl-out* (var *repl-out*))
;;(alter-var-root #'clojure.core/*out* (constantly *out*))
;(with-bindings {#'clojure.core/*out* *repl-out*}
;  (with-open [server (server/start-server)]
;    (binding [*server* server]
;              ;*out* george.application.repl/*out*]
;      ;(set! clojure.core/*out* *out*)
;      (println "*print-length*:" *print-length*)
;      (println "*print-level*:" *print-level*)
;      (println "*out*-:" (var *out*))
;      (pprint (do-eval (-> (repl-eval session "(println 5)(.println System/out 123)  *out* (var *out*)") repl/combine-responses))))))


;;;; DEV stuff



;(do (println "WARNING! Running george.application.repl/serve!") (serve!))
