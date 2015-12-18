(ns dev.util.java)


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

(defmacro vargs-1 [type & body]
    `(into-array ~type [~@body]))

;; (vargs "a" (str 42)) -> (into-array ["a" (str 42)])
;; (vargs-1 String "a" (str 42)) -> (into-array String ["a" (str 42)])
(comment prn (macroexpand-1 '(vargs "a" (str 42))))
(comment prn (macroexpand-1 '(vargs-1 String "a" (str 42))))
