;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
    ^{:doc "
    Most, but not all of the following works:
    http://danmidwood.com/content/2014/11/21/animated-paredit.html
    "}
    george.code.paredit
    (:require
        [paredit.core :as pe]
        [paredit.parser :as pep]
        [george.javafx :as fx]
        [george.code.codearea :as ca])

    (:import [org.fxmisc.richtext StyledTextArea]))


(def ^:dynamic *debug* false)


(defn exec-command [cmd ^StyledTextArea codearea]
    (let [txt (ca/text codearea)
          buffer (pep/edit-buffer nil 0 -1 txt)
          parse-tree (pep/buffer-parse-tree buffer :for-test)
          ;parse-tree (pep/parse txt)
          sel (.getSelection codearea)
          sel-start (.getStart sel)
          sel-end (.getEnd sel)
          caret (.getCaretPosition codearea)
          len (- sel-end sel-start)]

      (when *debug*
        (printf "caret: %s  selection: %s-%s  len: %s\n" caret sel-start sel-end len))
      (pe/paredit cmd
         {:parse-tree parse-tree :buffer buffer}
         ;{:parse-tree parse-tree}
         {:offset (if (#{:paredit-backward-delete} cmd) sel-end sel-start)
          :length len
          :text txt})))




(defn insert-result [^StyledTextArea codearea pe]
  (let [caret-left? (= (.getCaretPosition codearea)
                       (-> codearea .getSelection .getStart))
        {:keys [length offset]} pe]

    (when *debug* (println "caret-left?:" caret-left?))

    ;; make changes
    (doseq [{:keys [length offset text] :as mod} (:modifs pe)]
      (when *debug* (println "mod:" mod))
      (if (zero? length)
        (.insertText codearea offset text)
        (.replaceText codearea offset (+ offset length) text)))

    ;; adjust caret and selection
    (if (zero? length)
      (.selectRange codearea offset offset)
      (if caret-left?
        (.selectRange codearea (+ offset length) offset)
        (.selectRange codearea offset (+ offset length))))))


(comment def os-x-charmap
  {"‚" ")" ;;close and round newline
   "Æ" "\"" ;; meta double quote
   "…" ";"  ;; paredit-commit-dwim
   "∂" "d"  ;;paredit-forward-kill-word
   "·" "(" ;; paredit-wrap-round
   "ß" "s" ;;paredit splice
   "®" "r" ;; raise expr
   "Í" "S" ;; split
   "Ô" "J"}) ;;join


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
             (str keyChar)))]))



(comment defn key-pressed-handler [w]
  (reify java.awt.event.KeyListener

      (keyReleased [this e] nil)

      (keyTyped [this e]
          (when (#{"(" ")" "[" "]" "{" "}" "\""} (str (.getKeyChar e)))
              (.consume e)))

      (keyPressed [this e]
          (let [k (convert-key-event e)
                p (exec-paredit k w)]
              (when p (.consume e))))))



(comment defn input-method-event-handler [w]
  (reify java.awt.event.InputMethodListener
    (inputMethodTextChanged [this e]
      (let [k (convert-input-method-event e)
            p (exec-paredit k w)]
        (if p (.consume e))))))


(defn- consuming-commands [m]
    (into {}
         (map
           (fn [[k v]]
               [k (fx/event-handler-2 [_ e] (exec-paredit v (.getSource e))
                                            (.consume e))])
           m)))


(defn- nonconsuming-commands [m]
    (into {}
         (map
           (fn [[k v]]
               [k (fx/event-handler-2 [_ e] (exec-paredit v (.getSource e)))])
           m)))


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
             "\"" :paredit-doublequote})
             ;"\b"  :paredit-backward-delete

        (nonconsuming-commands
            {})))
             ;"\b"  :paredit-backward-delete


(def codes-map
    (conj
        (consuming-commands
            {
             #{:TAB}                    :paredit-indent-line
             #{:ENTER}                  :paredit-newline
             ;#{:ALT :RIGHT_PARENTHESIS} :paredit-close-round-and-newline
             ;#{:ALT :LEFT_PARENTHESIS}  :paredit-wrap-round
             #{:ALT :UP}                 :paredit-splice-sexp
             ;#{:ALT :DOWN}                 :paredit-raise-sexp
             #{:ALT :SHORTCUT :RIGHT}   :paredit-forward-slurp-sexp
             #{:ALT :SHORTCUT :LEFT}    :paredit-backward-slurp-sexp
             #{:ALT :SHORTCUT :SHIFT :RIGHT}    :paredit-forward-barf-sexp
             #{:ALT :SHORTCUT :SHIFT :LEFT}     :paredit-backward-barf-sexp
             ;#{:ALT :SHIFT :S}          :paredit-split-sexp
             ;#{:ALT :SHIFT :J}          :paredit-join-sexps
             ;#{:ALT :RIGHT}             :paredit-expand-right
             ;#{:ALT :LEFT}              :paredit-expand-left
             #{:BACK_SPACE}             :paredit-backward-delete
             #{:DELETE}                 :paredit-forward-delete})

        (nonconsuming-commands
          {})))

             ; #{:SHORTCUT :SHIFT :K} :paredit-kill not implemented in paredit.clj


(defn set-handlers [a]
    (doto a
      (.setOnKeyPressed (fx/key-pressed-handler codes-map))
      (.setOnKeyTyped (fx/char-typed-handler chars-map))))

