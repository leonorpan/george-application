;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns george.editor
  (:require
    [clojure.core.async :refer [>!! <! chan timeout sliding-buffer thread go go-loop]]
    [george.javafx.java :as j]
    [george.javafx :as fx]
    [george.javafx.util :as fxu]
    [george.code.core :as gcode]
    [george.application.output :refer [output]]
    [george.util :as u])


  (:import [javafx.beans.property StringProperty]
           [javafx.scene.control OverrunStyle]
           [javafx.beans.value ChangeListener]))


(defn load-from-file [file ns-str]
    ;(println "  ## load-from-file  ns:" ns-str)
  (binding [*ns* (create-ns (symbol ns-str))]
      ;(println "  ## *ns*:" *ns*)
      (println)
      (output :system (format "(load-file \"%s\")\n" file))
      (println)
      (load-file (str file))))



(defn load-via-tempfile [code-str ns-str]
    (println "  ## load-via-tempfile  ns:" ns-str)
    (let [temp-file (java.io.File/createTempFile "code_" ".clj")]
      (spit temp-file code-str)
      (load-from-file temp-file ns-str)))




(defn- update-file-label [file-meta file-label chrome-title]
    (when-let [f (:file @file-meta)]
        (fx/later
            (. chrome-title setValue (str (if (:changed @file-meta) "* " "") (. f getName)))
            (. file-label setText (str (. f getAbsolutePath))))))




(def clj-filechooser
    (apply fx/filechooser fx/FILESCHOOSER_FILTERS_CLJ))


(defn- select-file
    "returns an existing selected file or nil"
    []
    (when-let [f (-> (doto clj-filechooser (. setTitle "Select Clojure File ...")) (. showOpenDialog  nil))]
        (. clj-filechooser setInitialDirectory (. f getParentFile))
        f))


(defn- create-file
    "returns a new created file"
    []
    (when-let [f (-> (doto clj-filechooser (. setTitle "Save Clojure File as ...")) (. showSaveDialog nil))]
        (. clj-filechooser setInitialDirectory (. f getParentFile))
        f))


(defn- set-file
    "sets file-meta, sets file-label, reads data into textarea"
    [file file-meta file-label chrome-title]
    (swap! file-meta assoc :file file)
    (update-file-label file-meta file-label chrome-title))



(defn- save-file [data file-meta file-label ^StringProperty chrome-title]
    (when-let [f (:file @file-meta)]
        ;(println "saving data to file: " (str f))
        (spit f data)
        (swap! file-meta assoc :changed false)
        (update-file-label file-meta file-label chrome-title)))



(defn- save-channel [file-meta file-label chrome-title]
    (let [c (chan (sliding-buffer 1))]  ;; we only need to save latest update
        (go (while true
                (<! (timeout 5000))  ;; save latest update every 5 seconds
                (let [data (<! c)]
                    ;(println "save-channel got data ...")
                    (save-file data file-meta file-label chrome-title))))
      c))



(defn- codearea-changelistener [save-chan file-meta file-label chrome-title]
    (reify ChangeListener
        (changed [_ obs old-code new-code]
            ;(println "  ## code changed ...")
            (swap! file-meta assoc :changed true)
            (update-file-label file-meta file-label chrome-title)
            (>!! save-chan new-code))))



(defn code-editor-pane [^StringProperty chrome-title & args]
    (println " ## args:" args)
    (let [
          default-kwargs {:file nil :library nil :namespace "user"}
          [_ kwargs] (fxu/partition-args args default-kwargs)
          _ (println "code-editor-pane kwargs:" kwargs)

          codearea
          #_(fx/textarea
              :text ""
              :font (fx/SourceCodePro "Medium" 16))
          (doto
              (gcode/->codearea))
              ;(. setFont (fx/SourceCodePro "Medium" 16))


          file-meta
          (atom {:file (:file kwargs) :changed false})

          file-label
          (doto
              (fx/label "<unsaved file>")
              (.setTextOverrun OverrunStyle/LEADING_ELLIPSIS))

          save-file-fn
          #(save-file (gcode/text codearea) file-meta file-label chrome-title)

          set-file-fn
          (fn [file] (set-file file file-meta file-label chrome-title))

          save-file-as-fn
          #(when-let [f (create-file)]
              (save-file-fn)
              (set-file-fn f)
              (save-file-fn))

          open-file-fn
          #(when-let [f (select-file)]
              (save-file-fn)
              (set-file-fn f)
              (gcode/set-text codearea (slurp f)))


          open-file-button
          (fx/button "Open ..."
                     :minwidth 60
                     :width 60
                     :onaction open-file-fn
                     :tooltip (format "Select and open an existing (Clojure/Turtle) file ...  %s-O" u/SHORTCUT_KEY))


          file-pane
          (fx/hbox
              file-label
              (fx/region :hgrow :always)
              open-file-button
              (fx/button "Save as ..."
                         :minwidth 70
                         :width 70
                         :onaction save-file-as-fn
                         :tooltip (format "Save as a new (Clojure/Turtle) file ...\nChanges will be saved automatically every 5 seconds,\nor when 'Load' is clicked.  %s-SHIFT-S" u/SHORTCUT_KEY))

              :insets [0 0 10 0]
              :spacing 10)

          load-fn
          #(j/thread
              (println (if-let [f (:file @file-meta)]
                           (do (save-file-fn)
                               (load-from-file f (:namespace kwargs)))
                           (load-via-tempfile (gcode/text codearea) (:namespace kwargs)))))
          load-button
          (fx/button
              "Load"
              :minwidth 140
              :tooltip (format "Load/reload file.   %s-L" u/SHORTCUT_KEY)
              :onaction load-fn)

          pane
          (fx/borderpane
              :center codearea
              :top file-pane
              :bottom
              (fx/hbox
                  (fx/region :hgrow :always)
                  load-button
                  :insets [10 0 0 0])
                  ;:alignment Pos/TOP_RIGHT

              :insets 10)

          save-chan
          (save-channel file-meta file-label chrome-title)]



        (-> codearea
            .textProperty
            (.addListener (codearea-changelistener save-chan file-meta file-label chrome-title)))

        [pane load-fn open-file-fn save-file-as-fn save-file-fn]))



(defn new-code-stage [& args]
  ;(println "new-code-stage")
  (let [
        scene
        (doto
            (fx/scene (fx/group))  ;; temporary root
            (fx/add-stylesheets "styles/codearea.css"))


        stage
        (fx/now (fx/stage
                  :scene scene
                  :title "<unsaved file>"
                  ;:location (fx/centering-point-on-primary scene)
                  :location [300 80]
                  :size [800 600]))
                  ;:oncloserequest #(println "closing ...")
                  ;:onhidden #(println "... closed")


        [root load-fn open-file-fn save-file-as-fn save-file-fn]
        (apply code-editor-pane (cons (.titleProperty stage) args))]

      ;; replace empty group with pane bound to stages title)
      (doto scene
       (.setRoot root)
       (.setOnKeyPressed
          (fx/key-pressed-handler {
                                   #{:L :SHORTCUT} load-fn
                                   #{:O :SHORTCUT} open-file-fn
                                   #{:S :SHORTCUT :SHIFT} save-file-as-fn
                                   #{:S :SHORTCUT} save-file-fn})))


      stage))


;;; DEV ;;;

;(println "WARNING: Running george.editor/new-code-stage" (new-code-stage))

