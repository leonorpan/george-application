(ns george.javafx
    (:require
        [george.java :as j] :reload
        [george.javafx-classes :as fxc] :reload
        )
    (:import
    )

)

(fxc/import-classes)




(defn thread*
    "Utility function for 'thread'."
    [exp]
    (if (Platform/isFxApplicationThread)
        (exp)
        (Platform/runLater exp)))

(defmacro thread
    "Ensure running body in JavaFX thread."
    [& body]
    `(thread* (fn [] ~@body)))



(defn dont-exit! []
    (Platform/setImplicitExit false))


(defn init []
    "An easy way to 'initalize [JavaFX] Toolkit'
Needs only be called once in the applications life-cycle.
Has to be called before the first call to/on FxApplicationThread (javafx/thread)"
    (JFXPanel.))


(defmacro event-handler
    "Returns an instance of javafx.event.EventHander,
where input is ingored,
and the the body is called on 'handle' "

    [& body]
    `(reify EventHandler (~'handle [~'_ ~'_] ~@body)))

(defmacro event-handler-2
    "Returns an instance of javafx.event.EventHander,
where args-vec is a vector of 2 elements  - naming the bindings for 'this' and 'event',
and the body is called on 'handle'"
    [args-vec & body]
    (assert (vector? args-vec) "First argument must be a vector representing 2 args")
    (assert (= 2 (count args-vec)) "args-vector must contain 2 elements - for binding 'this' and 'event'")
    `(reify EventHandler (~'handle ~args-vec ~@body)))


; (event-handler (println 1) (println 2)) ->
; (reify EventHandler (handle [_ _] (println 1) (println 2)))
(comment macroexpand-1 '(event-handler
                    (println 1)
                    (println 2)))

; (event-handler-2 [t e] (println 1) (println 2)) ->
; (reify EventHandler (handle [t e] (println 1) (println 2)))
(comment macroexpand-1 '(event-handler-2 [t e]
                    (println 1)
                    (println 2)))



(defn XY[item]
    [(.getX item) (.getY item)])

(defn WH [item]
    (if (instance? Node item)
        (let [b (.getBoundsInParent item)]
            [(.getWidth b) (.getHeight b)])
        [(.getWidth item) (.getHeight item)]))


(defn make-border
    ([color]
        (make-border color 1.))
    ([color width]
        (make-border color width 0.))
    ([color width rad]
        (Border. (j/vargs
                     (BorderStroke.
                          color
                          BorderStrokeStyle/SOLID
                          (CornerRadii. rad)
                          (BorderWidths. width)
                          ))))
    )


(defn add-stylesheets [^Scene scene ^String & sheetpaths]
    (-> scene .getStylesheets (.addAll (into-array String sheetpaths))))

(defn add-stylesheet [^Scene scene ^String sheetpath]
    (-> scene .getStylesheets (.add sheetpath)))