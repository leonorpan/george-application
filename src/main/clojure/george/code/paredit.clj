(ns
  ^{:author "Terje Dahl" }
  george.code.paredit
  (:use
        [paredit.core]
        [paredit.parser])
  (:require
    [george.javafx :as fx] :reload
    [george.code.highlight :as highlight]
    :reload

    )
  )

(def ^:dynamic *debug* false)


(comment defn exec-command [cmd widget]
  (let [caretpos (.getCaretPosition widget)
        t (. widget getText)
        buffer (paredit.parser/edit-buffer nil 0 -1 t)
        parse-tree (paredit.parser/buffer-parse-tree buffer :for-test)]
    (paredit cmd
             {:parse-tree parse-tree :buffer buffer}
             {:text t, :offset (min (.getSelectionStart widget) (.getCaretPosition widget)), :length (- (.getSelectionEnd widget) (.getSelectionStart widget) )})))


(defn exec-command [cmd codearea]
    (let [caretpos (.getCaretPosition codearea)
          t (highlight/get-text codearea)
          buffer (paredit.parser/edit-buffer nil 0 -1 t)
          parse-tree (paredit.parser/buffer-parse-tree buffer :for-test)]
        (paredit cmd
                 {:parse-tree parse-tree :buffer buffer}
                 {:text t,
                  :offset (min (-> codearea .getSelection .getStart) (.getCaretPosition codearea)),
                  :length (- (-> codearea .getSelection .getEnd) (-> codearea .getSelection .getStart))
                  })))


(comment defn insert-result [w pe]
  (dorun (map #(if (= 0 (:length %))
                (.insert w (:text %) (:offset %))
                (.replaceRange w (:text %) (:offset %) (+ (:length %) (:offset %))))
              (:modifs pe)))
  (.setCaretPosition w (:offset pe))
  (if (< 0 (:length pe))
    (do
      (.setSelectionStart w (:offset pe))
      (.setSelectionEnd w (+ (:offset pe) (:length pe))))))



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

;    (.positionCaret w (:offset pe))
    (.selectRange w (:offset pe) (:offset pe))

    (when (< 0 (:length pe))
        (comment .selectRange w
                      (:offset pe)
                      (+ (:offset pe) (:length pe)))))


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


(comment def keymap
  {
   [nil "(" ] :paredit-open-round
   [nil ")" ] :paredit-close-round
   [nil "[" ] :paredit-open-square
   [nil "]" ] :paredit-close-square
   [nil "{" ] :paredit-open-curly
   [nil "}" ] :paredit-close-curly
   [nil "\b"] :paredit-backward-delete
   [nil  "\t"] :paredit-indent-line
   ["M" ")"] :paredit-close-round-and-newline
   [nil "\""] :paredit-doublequote
   [nil "DEL"] :paredit-forward-delete
   ; ["C" "K"] :paredit-kill not implemented in paredit.clj
   ["M" "("] :paredit-wrap-round
   ["M" "s"] :paredit-splice-sexp
   ["M" "r"] :paredit-raise-sexp
   ["C" "0"] :paredit-forward-slurp-sexp
   ["C" "9"] :paredit-backward-slurp-sexp
   ["C" "Close Bracket"] :paredit-forward-barf-sexp
   ["C" "Open Bracket"] :paredit-backward-barf-sexp
   [nil "\n"] :paredit-newline
   ["M" "S"] :paredit-split-sexp
   ["M" "J"] :paredit-join-sexps
   ["M" "Right"] :paredit-expand-right
   ["M" "Left"] :paredit-expand-left
   })


(comment defn exec-paredit [k w]
    (let [cmd (keymap k)]
        (when *debug* (println [cmd k]))
        (when cmd
            (let [result (exec-command cmd w)]
                (if *debug* (println [cmd result]))
                (insert-result w result)))
        cmd))


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



(defn set-handlers! [a]
    ;(. a setOnKeyPressed (key-pressed-handler a))
    ;(.addInputMethodListener a (input-method-event-handler a))

    (. a setOnKeyPressed (fx/key-pressed-handler codes-map))
    (. a setOnKeyTyped (fx/char-typed-handler chars-map))
    a)
