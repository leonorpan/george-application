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

(defmacro vargs-t [type & body]
    `(into-array ~type [~@body]))

;; (vargs "a" (str 42)) -> (into-array ["a" (str 42)])
;; (vargs-t String "a" (str 42)) -> (into-array String ["a" (str 42)])
(comment prn (macroexpand-1 '(vargs "a" (str 42))))
(comment prn (macroexpand-1 '(vargs-t String "a" (str 42))))
