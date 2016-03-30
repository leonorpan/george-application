(ns george.editor
    (:require
        [clojure.core.async :refer [>!! <! chan timeout sliding-buffer thread go go-loop]]
        [george.java :as j]
        :reload
        [george.javafx :as fx]
        :reload
        [dev.andante.highlight :as dah]
        :reload
        )
    )

(fx/import-classes!)


(defn load-from-file [file ns-str]
  (binding [*ns* (create-ns (symbol ns-str))]
    (printf "(load-file \"%s\")\n" file)
    (load-file (str file))
    ))


(defn load-via-tempfile [code-str ns-str]
    (let [temp-file (java.io.File/createTempFile "code_" ".clj")]
      (spit temp-file code-str)
      (load-from-file temp-file ns-str)
      ))



(defn- update-file-label [file-meta file-label chrome-title]
    (when-let [f (:file @file-meta)]
        (fx/later
            (. chrome-title setValue (str (if (:changed @file-meta) "* " "") (. f getName)))
            (. file-label setText (str (. f getAbsolutePath)))
            )))



(def clj-filechooser
    (doto (FileChooser.)
        (-> .getExtensionFilters (.addAll (j/vargs
              (FileChooser$ExtensionFilter. "Clojure Files" (j/vargs "*.clj"))
              (FileChooser$ExtensionFilter. "All Files" (j/vargs "*.*"))
              )))))


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
        (println "saving data to file: " (str f))
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
            (swap! file-meta assoc :changed true)
            (update-file-label file-meta file-label chrome-title)
            (>!! save-chan new-code))))



(defn code-editor-pane [^StringProperty chrome-title]
    (let [
          codearea
          #_(fx/textarea
              :text ""
              :font (fx/SourceCodePro "Medium" 16))
          (dah/codearea)

              file-meta
          (atom {:file nil :changed false})

          file-label
          (doto
              (Label. "<unsaved file>")
              (. setTextOverrun OverrunStyle/LEADING_ELLIPSIS))

          save-file-fn
          #(save-file (dah/get-text codearea) file-meta file-label chrome-title)

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
              (dah/set-text codearea (slurp f))
              )

          open-file-button
          (fx/button "Open ..."
                     :minwidth 60
                     :width 60
                     :onaction open-file-fn
                     :tooltip "Select  and open an existing (Clojure) file ..."
                     )

          file-pane
          (fx/hbox
              file-label
              (doto (Region.) (HBox/setHgrow Priority/ALWAYS))
              open-file-button
              (fx/button "Save as ..."
                         :minwidth 70
                         :width 70
                         :onaction save-file-as-fn
                         :tooltip "Save as a new (Clojure) file ...\nChanges will be saved automatically every 5 seconds,\nor when 'Load' is clicked."
                         )
              :insets [0 0 10 0]
              :spacing 10)

          pane
          (fx/borderpane
              :center codearea
              :top file-pane
              :bottom
              (fx/hbox
                  (doto (Region.) (HBox/setHgrow Priority/ALWAYS))
                  (fx/button
                      "Load"
                      :minwidth 140
                      :onaction
                      #(j/thread
                          (println (if-let [f (:file @file-meta)]
                                       (do (save-file-fn)
                                           (load-from-file f "user"))
                                       (load-via-tempfile (dah/get-text codearea) "user")))))

                  :insets [10 0 0 0]
                  ;:alignment Pos/TOP_RIGHT
                  )
              :insets 10)

          save-chan
          (save-channel file-meta file-label chrome-title)

          ]

        (-> codearea
            .textProperty
            (.addListener (codearea-changelistener save-chan file-meta file-label chrome-title)))

        pane))



(defn new-code-stage []
  ;(println "new-code-stage")
  (let [
        scene
        (doto
            (fx/scene (fx/group))  ;; temporary root
            (fx/add-stylesheets "styles/codearea.css")
            )

        stage
        (fx/now (fx/stage
                  :scene scene
                  :title "<unsaved file>"
                  :location (fx/centering-point-on-primary scene)
                  :sizetoscene true
                  ;:oncloserequest #(println "closing ...")
                  ;:onhidden #(println "... closed")
                  ))

        root
        (code-editor-pane (. stage titleProperty))
        ]
      ;; replace empty group with pane bound to stages title)
      (. scene setRoot root)

    stage))


