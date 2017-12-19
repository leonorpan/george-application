;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.editor.input
  (:require [george.javafx :as fx]
            [george.util.javafx :as ufx])

  (:import (javafx.event EventType)
           (javafx.scene.input MouseEvent MouseDragEvent KeyEvent KeyCode)
           (org.fxmisc.flowless VirtualFlow)))


(def MAC_SHIFT_TAB_CHAR (char 25))
(def WIN_CTRL_A_CHAR (char 1))
(def WIN_CTRL_C_CHAR (char 3))
(def WIN_CTRL_X_CHAR (char 24))
(def WIN_CTRL_V_CHAR (char 22))
(def WIN_CTRL_Z_CHAR (char 26))
(def WIN_DELETE_CHAR (char 127))

(def OPEN_SQARE_CHAR (char 91)) ;; [
(def CLOSE_SQARE_CHAR (char 93)) ;; ]
(def OPEN_CURLY_CHAR (char 123)) ;; {
(def CLOSE_CURLY_CHAR (char 125)) ;; }
(def AT_CHAR (char 64)) ;; @

(def win-problem-char-set #{OPEN_SQARE_CHAR
                            CLOSE_SQARE_CHAR
                            OPEN_CURLY_CHAR
                            CLOSE_CURLY_CHAR
                            AT_CHAR})


(defn code-actions [key-pressed-fn]
  {
   #{:UP}                   #(key-pressed-fn :move-up)
   #{:UP :ALT}              #(key-pressed-fn :move-up-step)  ;; straight up to top of text
   #{:UP :SHORTCUT}         #(key-pressed-fn :move-up-limit) ;; to the very beginning of the text
   #{:UP :SHIFT}            #(key-pressed-fn :select-up)
   #{:UP :SHIFT :ALT}       #(key-pressed-fn :select-up-step)
   #{:UP :SHIFT :SHORTCUT}  #(key-pressed-fn :select-up-limit)

   #{:DOWN}                   #(key-pressed-fn :move-down)
   #{:DOWN :ALT}              #(key-pressed-fn :move-down-step)
   #{:DOWN :SHORTCUT}         #(key-pressed-fn :move-down-limit)
   #{:DOWN :SHIFT}            #(key-pressed-fn :select-down)
   #{:DOWN :SHIFT :ALT}       #(key-pressed-fn :select-down-step)
   #{:DOWN :SHIFT :SHORTCUT}  #(key-pressed-fn :select-down-limit)

   #{:LEFT}                   #(key-pressed-fn :move-left)
   #{:LEFT :ALT}              #(key-pressed-fn :move-left-step)
   #{:LEFT :SHORTCUT}         #(key-pressed-fn :move-left-limit)
   #{:LEFT :SHIFT}            #(key-pressed-fn :select-left)
   #{:LEFT :SHIFT :ALT}       #(key-pressed-fn :select-left-step)
   #{:LEFT :SHIFT :SHORTCUT}  #(key-pressed-fn :select-left-limit)

   #{:RIGHT}                   #(key-pressed-fn :move-right)
   #{:RIGHT :ALT}              #(key-pressed-fn :move-right-step)
   #{:RIGHT :SHORTCUT}         #(key-pressed-fn :move-right-limit)
   #{:RIGHT :SHIFT}            #(key-pressed-fn :select-right)
   #{:RIGHT :SHIFT :ALT}       #(key-pressed-fn :select-right-step)
   #{:RIGHT :SHIFT :SHORTCUT}  #(key-pressed-fn :select-right-limit)

   #{:ENTER}       #(key-pressed-fn :enter)
   #{:BACK_SPACE}  #(key-pressed-fn :backspace)
   #{:DELETE}      #(key-pressed-fn :delete)

   #{:TAB}         #(key-pressed-fn :tab)
   #{:SHIFT :TAB}  #(key-pressed-fn :untab)})


(defn char-actions [key-pressed-fn]
  {
   ;; Both cases - in case user has (accidentally) activated Tabs Lock
   #{:SHORTCUT \A}              #(key-pressed-fn :selectall)
   #{:SHORTCUT \a}              #(key-pressed-fn :selectall)
   #{:SHORTCUT WIN_CTRL_A_CHAR} #(key-pressed-fn :selectall)
   #{:SHORTCUT \C}              #(key-pressed-fn :copy)
   #{:SHORTCUT \c}              #(key-pressed-fn :copy)
   #{:SHORTCUT WIN_CTRL_C_CHAR} #(key-pressed-fn :copy)
   #{:SHORTCUT \X}              #(key-pressed-fn :cut)
   #{:SHORTCUT \x}              #(key-pressed-fn :cut)
   #{:SHORTCUT WIN_CTRL_X_CHAR} #(key-pressed-fn :cut)
   #{:SHORTCUT \V}              #(key-pressed-fn :paste)
   #{:SHORTCUT \v}              #(key-pressed-fn :paste)
   #{:SHORTCUT WIN_CTRL_V_CHAR} #(key-pressed-fn :paste)

   #{:SHORTCUT \Z}              #(key-pressed-fn :undo)
   #{:SHORTCUT \z}              #(key-pressed-fn :undo)
   #{:SHORTCUT WIN_CTRL_Z_CHAR} #(key-pressed-fn :undo)
   #{:SHORTCUT :SHIFT \Z}              #(key-pressed-fn :redo)
   #{:SHORTCUT :SHIFT \z}              #(key-pressed-fn :redo)
   #{:SHORTCUT :SHIFT WIN_CTRL_Z_CHAR} #(key-pressed-fn :redo)

   ;; simply consume these
   #{\tab}                       #(do)
   #{:SHIFT \tab}                #(do)
   #{:SHIFT MAC_SHIFT_TAB_CHAR}  #(do)
   #{\return}                    #(do)
   #{:SHIFT \return}             #(do)
   #{\backspace}                 #(do)
   #{:SHIFT \backspace}          #(do)
   #{WIN_DELETE_CHAR}            #(do)})


   ;; save (and maybe save-as) should have state-API functions, but GUI is separate.
   ;; varieties find and replace are should be implemented in the george.editor.buffer-API, but GUI is separate.
   ;; open and close are file handling and should be implemented in (GUI) separately


(defn key-event-handler [key-pressed-fn char-entered-fn]
  (let [codes (code-actions key-pressed-fn)
        chars (char-actions key-pressed-fn)]

    (fx/event-handler-2 [_ e]
       (let [e-type (.getEventType e)]

         (when (= e-type KeyEvent/KEY_PRESSED)
           (let [combo (ufx/code-modifier-set e)]

             (when-let [f (codes combo)]
               (f)
               (.consume e))))

         (when (= e-type KeyEvent/KEY_TYPED)
           (let [combo (ufx/char-modifier-set e)]
             (doseq [c combo]
               (when (and (char? c) (win-problem-char-set c))
                 ;(println "Handled a problem-char:" c)
                 (char-entered-fn c)
                 (.consume e)))

             (when-let [f (chars combo)]
               (f)
               (.consume e))

             (when-not (.isConsumed e)
               (when (combo :SHORTCUT)
                 (.consume e)))

             (when-not (.isConsumed e)
               (when-let [ch (ufx/char-ensured e)]
                 (char-entered-fn ch)
                 (.consume e)))))))))


(defn- hit-data [^VirtualFlow flow e]
  (let [x (.getX e)
        y (.getY e)
        hit (.hit flow x y)
        hit? (.isCellHit hit)]
    (when hit?
      (let [row (.getCellIndex hit)
            offset (.getCellOffset hit)
            offset-x (.getX offset)
            cell (.getCell flow row)
            col (.getColumn cell offset-x)]

        [cell row col]))))


(defn mouse-event-handler [flow mouse-action-fn]
  (let [mouse-state_ (atom nil)]
    (fx/event-handler-2
      [_ e]
      (.requestFocus flow)
      (let [
            e-typ (.getEventType e)
            ;_ (println "  ## e-typ:" e-typ)
            e-typ-kw
            (condp = e-typ
              ;MouseEvent/MOUSE_CLICKED :clicked
              MouseEvent/MOUSE_PRESSED :pressed
              MouseEvent/MOUSE_DRAGGED :dragged
              MouseDragEvent/MOUSE_DRAG_OVER :drag-over
              MouseEvent/MOUSE_RELEASED :released
              MouseDragEvent/MOUSE_DRAG_RELEASED :drag-released
              nil)
            sel-or-move (if (.isShiftDown e) :select :move)
            [_ row col :as hit] (hit-data flow e)]
        (if hit
          (mouse-action-fn @mouse-state_ e-typ-kw sel-or-move [row col])
          (mouse-action-fn @mouse-state_ e-typ-kw sel-or-move  :end))
        (.consume e)
        (reset! mouse-state_ e-typ-kw)))))
