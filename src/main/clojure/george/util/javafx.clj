;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:doc    "Augments and gradually replaces george.javafx.

The reason for replacing its that george.javafx is by no means a complete or consistent javafx API or framework. In stead it has grown organically based on the needs of the application. As such functionality form george.javafx will be moved here, hopefully with better/cleaner/more consistent design.

As a convention in this codebase, prefer to do
(ns ... (:require [george.util.javafx :as ufx]))
"
    :author "Terje Dahl"}
  george.util.javafx
  (:import (javafx.scene.input KeyEvent)))


(defn char-ensured
  "Returnes the char that was typed,
  else empty-flag or nil if no char was found in event,
  else undefined-flag or nil if the found char was not Character/isDefined"
 ([^KeyEvent event]
  (char-ensured event nil nil))
 ([^KeyEvent event empty-flag undefined-flag]
  (let [ch-str (.getCharacter event)]
    (if (empty? ch-str)
      empty-flag
      (let [ch (.charAt ch-str 0)]
        (if-not (Character/isDefined ch)
          undefined-flag
          ch))))))



(defn code-modifier-set
  "Returns a set of keywords (all caps) for key-code and active modifier-keys extracted form event. Useful for handling KEY_PRESSED / KEY_RELEASED events.
  Ex: #{:UP :SHORTCUT} or #{:BACKSPACE}  or #{:SHIFT :ENTER}"
  [^KeyEvent event]
  (let [code (str (.getCode event))
        shift (when (.isShiftDown event) "SHIFT")
        shortcut (when (.isShortcutDown event) "SHORTCUT")  ;; CTRL / CMD / "C-"
        alt (when (.isAltDown event) "ALT")] ;;  "M-"
    (set (map keyword (filter some? [code shift shortcut alt])))))


(defn char-modifier-set
  "Similar to code-modifier-set, but in stead of code, returns char+modifier-keywrods. Useful for handling KEY_TYPED events. Returns an empty set with :CHARACTER_UNDEFINED or :CHARACTER_EMPTY if that is the case.
  Ex: #{:SHORTCUT \\a} or #{\\A}"
  [^KeyEvent event]
  (let [ch-str (.getCharacter event)]
    (if (empty? ch-str)
      #{:CHARACTER_EMPTY}
      (let [ch (.charAt ch-str 0)]
           (if-not (Character/isDefined ch)
             #{:CHARACTER_UNDEFINED}
             (let [
                   shift (when (.isShiftDown event) "SHIFT")
                   shortcut (when (.isShortcutDown event) "SHORTCUT")
                   alt (when (.isAltDown event) "ALT")]
               (set (conj (map keyword (filter some? [shift shortcut alt])) ch))))))))


