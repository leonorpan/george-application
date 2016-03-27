(ns george.java)


(defn thread*
    "Utility fuction for 'thread'."
    [exp]
    (doto (Thread. exp)
        (.start)))

(defmacro thread
    "Run body in new thread."
    [& body]
    `(thread* (fn [] ~@body)))


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