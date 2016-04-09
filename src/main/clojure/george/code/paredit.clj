(ns
  ^{:author "Terje Dahl" }
  george.code.paredit
  (:require
      [paredit.core :as pe]
      [paredit.parser :as pep]
      [george.javafx :as fx] :reload
      [george.code.highlight :as highlight] :reload
    )
    )

(def ^:dynamic *debug* false)





(defn exec-command [cmd codearea]
    (let [t (highlight/get-text codearea)
          buffer (pep/edit-buffer nil 0 -1 t)
          parse-tree (pep/buffer-parse-tree buffer :for-test)]
        (pe/paredit cmd
                 {:parse-tree parse-tree :buffer buffer}
                 {:text t,
                  :offset (min (-> codearea .getSelection .getStart) (.getCaretPosition codearea)),
                  :length (- (-> codearea .getSelection .getEnd) (-> codearea .getSelection .getStart))
                  })))




(defn insert-result [w pe]
    (dorun
        (map
            #(if (= 0 (:length %))
                (.insertText w
                             (:offset %)
                             (:text %))
                (.replaceText w
                              (:offset %)  (+ (:length %) (:offset %))
                              (:text %)))
            (:modifs pe)))

    ;; position the caret
    (.selectRange w (:offset pe) (:offset pe))

    ;; Yes, this does shift the caret to the right of the selection,
    ;; but that's OK for now.
    (when (< 0 (:length pe))
        (.selectRange w
                      (:offset pe)
                      (+ (:offset pe) (:length pe))
                      )))


(comment def os-x-charmap
  {"‚" ")" ;;close and round newline
   "Æ" "\"" ;; meta double quote
   "…" ";"  ;; paredit-commit-dwim
   "∂" "d"  ;;paredit-forward-kill-word
   "·" "(" ;; paredit-wrap-round
   "ß" "s" ;;paredit splice
   "®" "r" ;; raise expr
   "Í" "S" ;; split
   "Ô" "J" ;;join
   })








(defn exec-paredit [cmd codearea]
    (let [result (exec-command cmd codearea)]
        (when *debug* (println [cmd result]))
        (insert-result codearea result))
    cmd)


(comment defn convert-input-method-event [event]
  ["M" (os-x-charmap (str (.first (.getText event))))])



(comment defn convert-key-event [event]
  (let [
        keyCode (.getKeyCode event)
        keyChar (.getKeyChar event)
        keyText (java.awt.event.KeyEvent/getKeyText keyCode)]

    (if *debug* (println  [event keyCode keyChar keyText]))
    [
     (cond
         (.isAltDown event) "M"
         (.isControlDown event) "C"
         true nil)

     (if (.isControlDown event)
         keyText
         (if (#{"Left" "Right"} keyText)
             keyText
             (str keyChar)))
     ]
    ))



(comment defn key-pressed-handler [w]
  (reify java.awt.event.KeyListener

      (keyReleased [this e] nil)

      (keyTyped [this e]
          (when (#{"(" ")" "[" "]" "{" "}" "\""} (str (.getKeyChar e) ))
              (.consume e)))

      (keyPressed [this e]
          (let [k (convert-key-event e)
                p (exec-paredit k w)]
              (when p (.consume e))))
      ))


(comment defn input-method-event-handler [w]
  (reify java.awt.event.InputMethodListener
    (inputMethodTextChanged [this e]
      (let [k (convert-input-method-event e)
            p (exec-paredit k w)]
        (if p (.consume e))))))


(defn- consuming-commands [-map]
    (->> -map (map (fn [[k v]]
                       [k
                        (fx/event-handler-2
                            [_ e]
                            (exec-paredit v (. e getSource)) (. e consume))]))
         (into {})))


(defn- nonconsuming-commands [-map]
    (->> -map (map (fn [[k v]]
                       [k
                        (fx/event-handler-2
                            [_ e]
                            (exec-paredit v (. e getSource)))]))
         (into {})))


(def chars-map
    (conj
        (consuming-commands
            {
             "("  :paredit-open-round
             ")"  :paredit-close-round
             "["  :paredit-open-square
             "]"  :paredit-close-square
             "{"  :paredit-open-curly
             "}"  :paredit-close-curly
             "\"" :paredit-doublequote
             ;"\b"  :paredit-backward-delete
             })
        (nonconsuming-commands
            {
             ;"\b"  :paredit-backward-delete
             })))

(def codes-map

    (conj
        (consuming-commands
            {
             #{:TAB}                    :paredit-indent-line
             #{:ENTER}                  :paredit-newline
             #{:ALT :RIGHT_PARENTHESIS} :paredit-close-round-and-newline
             #{:ALT :LEFT_PARENTHESIS}  :paredit-wrap-round
             #{:ALT :S}                 :paredit-splice-sexp
             #{:ALT :R}                 :paredit-raise-sexp
             #{:ALT :DIGIT0}            :paredit-forward-slurp-sexp
             #{:ALT :DIGIT9}            :paredit-backward-slurp-sexp
             #{:ALT :CLOSE_BRACKET}     :paredit-forward-barf-sexp
             #{:ALT :OPEN_BRACKET}      :paredit-backward-barf-sexp
             #{:ALT :SHIFT :S}          :paredit-split-sexp
             #{:ALT :SHIFT :J}          :paredit-join-sexps
             #{:ALT :RIGHT}             :paredit-expand-right
             #{:ALT :LEFT}              :paredit-expand-left
             ; #{:CTRL :SHIFT :K} :paredit-kill not implemented in paredit.clj
             })
        (consuming-commands
            {
             ;; Not able to handle these probably yet. TODO: Maybe later ...
             ;#{:BACK_SPACE}  :paredit-backward-delete
             ;#{:DELETE} :paredit-forward-delete
             })))



(defn set-handlers [a]
    ;(. a setOnKeyPressed (key-pressed-handler a))
    ;(.addInputMethodListener a (input-method-event-handler a))

    (. a setOnKeyPressed (fx/key-pressed-handler codes-map))
    (. a setOnKeyTyped (fx/char-typed-handler chars-map))
    a)
