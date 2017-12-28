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
        [clojure.pprint :refer [pprint]]
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
          sel (.getSelection codearea)
          sel-start (.getStart sel)
          sel-end (.getEnd sel)
          caret (.getCaretPosition codearea)
          len (- sel-end sel-start)

          parsetree-m {:parse-tree parse-tree :buffer buffer}
          state-m {:offset (if (#{:paredit-backward-delete} cmd) sel-end sel-start)
                   :length len
                   :text txt}]
      (when *debug*
        (printf "cmd: %s  caret: %s  selection: %s-%s  len: %s\n" cmd caret sel-start sel-end len)
        ;(pprint ["parsetree-m:" parsetree-m])
        (pprint ["state-m:" state-m]))
      (pe/paredit cmd
         parsetree-m
         state-m)))

(defn- correct-pairing
  "A hack to correct pairing due to strange bug related to after-comment-line.
  A single paren should never be inserted unpaired at the end of the code.
  Only correct if at the very end of the text (as this is the only case the bug  seems to appear)."
  [codearea offset s]
  ;(println "/correct-paring" "offset:" offset "ca len:" (.getLength codearea))
  (if (not= offset (.getLength codearea))
    s
    (case s
      "(" "()"
      ")" "()"
      "[" "[]"
      "]" "[]"
      "{" "{}"
      "}" "{}"
      s))) ;; default


(defn insert-result [^StyledTextArea codearea pe cmd]
  (let [caret-left? (= (.getCaretPosition codearea)
                       (-> codearea .getSelection .getStart))
        {:keys [length offset]} pe
        delete? (boolean (#{:paredit-backward-delete :paredit-forward-delete} cmd))]

    (when *debug* (println "caret-left?:" caret-left?))
    (when *debug* (println "delete?:" delete?))

    ;; make changes
    (doseq [{:keys [length offset text] :as mod} (:modifs pe)]
      (when *debug* (println "mod:" mod))
      (if (zero? length)
        (.insertText codearea offset  (correct-pairing codearea offset text))
        (.replaceText codearea offset (+ offset length) text)))

    ;; adjust caret and selection
    (if (or (zero? length) delete?)
      (.selectRange codearea offset offset)
      (if caret-left?
        (.selectRange codearea (+ offset length) offset)
        (.selectRange codearea offset (+ offset length))))))


(defn exec-paredit [cmd codearea]
  (try
    (let [result (exec-command cmd codearea)]
        (when *debug* (println [cmd result]))
        (insert-result codearea result cmd))
    (catch NullPointerException npe  ;; splice throws exception when no mor parens!
      (when *debug* (.printStackTrace npe)))
    (catch StringIndexOutOfBoundsException ooe  ;;
      (when *debug* (.printStackTrace ooe)))))


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
       #{:TAB}             :paredit-indent-line
       #{:ENTER}           :paredit-newline
       #{:BACK_SPACE}      :paredit-backward-delete
       #{:DELETE}          :paredit-forward-delete

       #{:ALT :UP}         :paredit-splice-sexp
       #{:ALT :DOWN}       :paredit-wrap-round

       #{:ALT :ENTER}           :paredit-split-sexp
       #{:ALT :SHIFT :ENTER}    :paredit-join-sexps

       #{:ALT :RIGHT}         :paredit-forward-slurp-sexp
       #{:ALT :SHIFT :RIGHT}  :paredit-forward-barf-sexp

       #{:ALT :LEFT}          :paredit-backward-slurp-sexp
       #{:ALT :SHIFT :LEFT}   :paredit-backward-barf-sexp})


    ;#{:ALT :RIGHT_PARENTHESIS} :paredit-close-round-and-newline
    ;#{:ALT :DOWN}                 :paredit-raise-sexp

    ;#{:ALT :RIGHT}             :paredit-expand-right
    ;#{:ALT :LEFT}              :paredit-expand-left

    (nonconsuming-commands
      {})))

             ; #{:SHORTCUT :SHIFT :K} :paredit-kill not implemented in paredit.clj

(defn key-pressed-handler []
  (fx/key-pressed-handler codes-map))

(defn key-typed-handler []
  (fx/char-typed-handler chars-map))
