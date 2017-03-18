;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.javafx.java
    (:import (javax.swing SwingUtilities)))


(defn thread*
    "Utility fuction for 'thread'."
    [exp]
    (doto (Thread. exp)
        (.start)))

(defmacro thread
    "Run body in new thread."
    [& body]
    `(thread* (fn [] ~@body)))

(defn swing*
    "Runs thunk in the Swing event thread according to schedule:
    - :later => schedule the execution and return immediately
    - :now   => wait until the execution completes."
    [schedule thunk]
    (cond
        (= schedule :later)
        (SwingUtilities/invokeLater thunk)
        (= schedule :now)
        (if (SwingUtilities/isEventDispatchThread)
            (thunk)
            (SwingUtilities/invokeAndWait thunk)))
    nil)


(defmacro swing
    "Executes body in the Swing event thread asynchronously. Returns
    immediately after scheduling the execution."
    [& body]
    `(swing* :later (fn [] ~@body)))


(defmacro swing-and-wait
    "Executes body in the Swing event thread synchronously. Returns
    after the execution is complete."
    [& body]
    `(swing* :now (fn [] ~@body)))


(defmacro vargs [& body]
    `(into-array [~@body]))

(defmacro vargs* [body]
    `(into-array ~body))

(defmacro vargs-t [typ & body]
    (assert (instance? Class (eval typ)) "First argument must be of type Class")
    `(into-array ~typ [~@body]))

(defmacro vargs-t* [typ body]
    (assert (instance? Class (eval typ)) "First argument must be of type Class")
    `(into-array ~typ ~body))

;; (vargs " a " (str 42)) -> (into-array [" a " (str 42)])
;; (vargs-t String " a " (str 42)) -> (into-array String [" a " (str 42)])
(comment prn (macroexpand-1 '(vargs " a " (str 42))))
(comment prn (macroexpand-1 '(vargs-t String "a" (str 42))))


(defmacro import! [specs]
    `(import ~@(if (instance? clojure.lang.Symbol specs) (eval specs) specs)))

(defn singlethreadexecutor []
    (java.util.concurrent.Executors/newSingleThreadExecutor))

(defn singlethreadexecute-fn []
    (let [exec (singlethreadexecutor)]
        (fn [f] (. exec execute f))))