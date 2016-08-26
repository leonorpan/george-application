(ns george.simple-editor
    (:require
        [clojure.repl :refer [doc]]
        [clojure.string :as s]
        [clojure.java.io :as cio]

        [george.java :as j] :reload
        [george.javafx :as fx] :reload
        [george.javafx-classes :as fxc] :reload
        [george.output :as output] :reload))






"
http://www.drdobbs.com/jvm/a-javafx-text-editor-part-1/240142297
http://www.drdobbs.com/jvm/a-javafx-file-editor-part-2/240142542
"


(fx/init)
(fxc/import-classes)

(def BROWSER "browser")
(def EDITOR "new editor")

(def SPEC_KEYCODE_SET #{KeyCode/BACK_SPACE KeyCode/ENTER KeyCode/DELETE})

(def browser-count (atom 0))

;(def current-tab (atom nil))

(def editors (atom []))
(def current-editor (atom nil))
(def ignore-next-press (atom false))

(def stage-singelton (atom nil))




(definterface IContent
    (getRoot []))

(definterface ISimpleEditor
    (isModified [])
    (setModified [modified])
    (getFile [])
    (setFile [text])
    (setTab [tab])
    (getTab [])
    (setText [ text])
    (getText []))


(deftype SimpleEditor [
                       textarea
                       ^:volatile-mutable filename
                       ^:volatile-mutable modified
                       ^:volatile-mutable tab]

    IContent
    (getRoot [_] textarea)
    ISimpleEditor
    (isModified [_] modified)
    (setModified [_ b] (set! modified b))
    (getFile [_] filename)
    (setFile [_ s] (set! filename s))
    (getTab [_] tab)
    (setTab [_ t] (set! tab t))
    (setText [_ text] (. textarea setText text))
    (getText [this] (. textarea getText)))




(defn- create-simple-editor []
    (->SimpleEditor (TextArea.) nil false nil))

(defn- create-simple-editor-and-register []
    (-> (swap! editors conj (create-simple-editor)) last))


(defn- create-web-browser []
    (let [
            DEFAULT_URL
            "https://duckduckgo.com/"

            web-view
            (WebView.)

            web-engine
            (. web-view getEngine)

            location-field
            (TextField. DEFAULT_URL)

            listener
            (reify ChangeListener
                (changed [_ _ _ new-value]
                    (. location-field setText new-value)))

            _ (-> web-engine .locationProperty (. addListener listener))


            go-action
            (fx/event-handler
                (let [location (. location-field getText)]
                    (. web-engine load
                        (if (. location startsWith "http")
                            location
                            (str "http://" location)))))

            _ (. location-field setOnAction go-action)

            go-button
            (doto
                (Button. "Go")
                (. setDefaultButton true)
                (. setOnAction go-action))

            hbox
            (HBox. 5 (j/vargs-t Node location-field go-button))
            _ (HBox/setHgrow location-field Priority/ALWAYS)

;            vbox
            root
            (VBox. 5 (j/vargs-t Node hbox web-view))
            _ (VBox/setVgrow web-view Priority/ALWAYS)]

;            root
;            (VBox. vbox)



        (reify IContent
            (getRoot [_] root))))



(defn- ^SimpleEditor editor-for-textarea [area]
    (first (filter #(= area (. % getRoot)) @editors)))



(defn- add-and-select-tab [tab-pane tab]
    (doto tab-pane
        (-> .getTabs (. add tab))
        (-> .getSelectionModel (. select tab)))
    ;(reset! current-tab tab)
    nil)


(defn- ^Tab create-new-into-tab [type-str]
    (let [
             content
             (condp = type-str
                 EDITOR
                 (create-simple-editor-and-register)
                 BROWSER
                 (create-web-browser))


             tab
             (Tab.
                 (if (= type-str BROWSER)
                     (str type-str " " (swap! browser-count inc))
                     type-str)
                 (. content getRoot))]


        (if (instance? SimpleEditor content) (. content setTab tab))

        tab))



(defn- ^Tab choose-and-load-file-into-tab []
    (let [
            fc
            (FileChooser.)

            file-to-open
            (. fc showOpenDialog nil)]


        (when file-to-open
            (let [
                    sb
                    (StringBuffer.)

                      ;; bis is FileInputStream wrapped in BufferedInputSream
                    _ (with-open [bis (cio/input-stream file-to-open)]
                        ;; TODO: reading one char at a time is very inefficient. Do something else!
                        (while (> (. bis available) 0)
                            (. sb append (char (. bis read)))))

                    editor
                    (doto (create-simple-editor-and-register)
                        (. setText (. sb toString))
                        (. setFile file-to-open))

                    tab
                    (Tab.
                        (. file-to-open getName)
                        (. editor getRoot))]

                 (. editor setTab tab)

                tab))))


(defn- has-extension [file]
    (-> file .getName (. contains ".")))


(defn- ensure-extension! [file]
    "has side-effect: Will delete file with old name, if exists."
    (if (has-extension file)
        file
        (let [
                path
                (. file getAbsolutePath)

                new-path
                (str path ".txt")

                new-file
                (cio/file new-path)]


            ;; TODO: Very bad! Existing file by same name gets overwritten!!! Pop-up a warning/question!!!
            (. file delete)
            new-file)))



(defn- write-file [file text]
    ;; TODO: this is not safe! do tempfile first, then swap at OS level if write was successful
    (with-open [bos (cio/output-stream file)]
        (doto bos (. write (. text getBytes)) (. flush))
        true))



(defn- indicate-file-modified []
    (println "indicate-file-modified")
    (when-let [editor @current-editor]
;        (assert editor "editor is nil")
        (when-not (. editor isModified)
            (println "Indicating text modified ...")
            (-> editor .getTab
                (. setText
                    (str "* "
                        (if-let [f (. editor getFile)]
                            (. f getName)
                            (-> editor .getTab .getText)))))
            (. editor setModified true))))


(defn- indicate-file-saved []
    (println "indicate-file-saved")
    (when-let [editor @current-editor]
;        (assert editor "editor is nil")
        (when (. editor isModified)
            (println "Indicating text saved")
            (-> editor .getTab (. setText  (-> editor .getFile .getName)))
            (. editor setModified false))))



(defn- new-file! [editor]
    ;; else - new file.  Need to choose name and location.
    (when-let [new-f (. (FileChooser.) showSaveDialog nil)]
        ;; TODO: BUG: If file chosen with ENTER-key, then keystroke caused editor to be marked as modified!
        (let [new-f (ensure-extension! new-f)]
            (. editor setFile new-f)
            new-f)))



(defn- save-file-rev []
    (println "save-file-rev")
    (when-let [editor @current-editor]
;        (assert editor "editor is nil")
        (let [
                file
                (. editor getFile)

                file
                (if file
                    file
                    (new-file! editor))]

            (when file
                (if (write-file file (. editor getText))
                    (indicate-file-saved)
                    (println "Error. File save failed!"))))))




(defn- handle-key-press [ke]
    (let [
            modifier?
            (or (. ke isControlDown) (. ke isMetaDown))

            code (. ke getCode)
            text (. ke getText)]

        (println "modifier?:" modifier?)
        (cond

            (and modifier? (. text equalsIgnoreCase "s"))
            (do (save-file-rev) (reset! ignore-next-press true))

            (not @ignore-next-press)
            (when
                (or
                    (SPEC_KEYCODE_SET code)
                    (and (not (empty? text)) (not modifier?)))
                (indicate-file-modified)))))




(defn- save-modifications []
    (doseq [editor @editors]
        (when (. editor isModified)
            (-> editor .getTab .getTabPane .getSelectionModel (. select (. editor getTab)))
            (save-file-rev))))


(defn simple-editor-scene []
    (let [

             tab-listener
             (reify ChangeListener
                 (changed [_ observable prev-tab new-tab]
                     (def new-tab new-tab)
                     (reset! current-editor (editor-for-textarea (. new-tab getContent)))))

            tab-pane
            (doto (TabPane.)
                (-> .getSelectionModel .selectedItemProperty (.addListener tab-listener)))

            menu-file-new
            (doto (MenuItem. "New")
                (. setOnAction
                    (fx/event-handler
                        (add-and-select-tab tab-pane  (create-new-into-tab EDITOR)))))

            menu-file-open
            (doto (MenuItem. "Open")
                (. setOnAction
                    (fx/event-handler
                        (add-and-select-tab tab-pane (choose-and-load-file-into-tab)))))

            menu-file-save
            (doto (MenuItem. "Save")
                (. setOnAction
                    (fx/event-handler
                        (save-file-rev))))

            menu-file-exit
            (doto (MenuItem. "Exit")
                (. setOnAction
                    (fx/event-handler
                        (save-modifications)
                        (reset! stage-singelton (. @stage-singelton close)))))

            menu-file
            (Menu. "File" nil
                      (j/vargs menu-file-new menu-file-open menu-file-save menu-file-exit))

            menu-view-URL
            (doto (MenuItem. "Web Page")
                (. setOnAction
                    (fx/event-handler
                        (add-and-select-tab tab-pane (create-new-into-tab BROWSER)))))

            menu-view
            (Menu. "View" nil
                      (j/vargs menu-view-URL))

            menu-bar
            (MenuBar. (j/vargs menu-file menu-view))

            layout
            (doto (VBox. 10 (j/vargs-t Node menu-bar tab-pane))
                (. setFillWidth true))

            scene
            (Scene. layout 800 600)]



        (doto tab-pane
            (-> .prefWidthProperty (. bind (. scene widthProperty)))
            (-> .prefHeightProperty (. bind (. scene heightProperty))))

        (doto scene
            (. setOnKeyPressed
                (fx/event-handler-2 [_ ke]
                    (let [
                             text (. ke getText)
                             code (. ke getCode)]

                        (printf "onKeyPressed: code=%s, text=%s\n" code text)
                        (handle-key-press ke))))
            (. setOnKeyReleased
                (fx/event-handler-2 [_ ke]
                    (let [
                             text (. ke getText)
                             code (. ke getCode)]

                        (when (SPEC_KEYCODE_SET code)
                            (indicate-file-modified))
                        ;; After the "s" is pressed to invoke a save action, make
                        ;; sure the subsequent release doesn't mark the file
                        ;; to be saved once again
                        (if (not (or (. ke isControlDown) (. ke isMetaDown)))
                            (if (and (= text "s") @ignore-next-press)
                                (reset! ignore-next-press false)
                                (handle-key-press ke)))))))

        (add-and-select-tab tab-pane  (create-new-into-tab EDITOR))

        scene))




(defn show-simple-editor-stage []
    (let [
             scene
             (simple-editor-scene)

             stage
             (doto (Stage.)
                 (. setScene scene)
                 (. sizeToScene)
                 ;                      (. centerOnScreen)
                 (. setX (-> (Screen/getPrimary) .getVisualBounds .getWidth (/ 2)))
                 (. setY (-> (Screen/getPrimary) .getVisualBounds .getHeight (/ 2) (- 300)))
                 (. setTitle "Simple Editor / Browser")
                 (. show)
                 (. toFront))]

        (reset! stage-singelton stage)
        nil))



;;;; dev ;;;;

(defn -main
    [& args]
    (println "george.simple-editor/-main")
    (fx/dont-exit!)
    (fx/thread (show-simple-editor-stage)))

;(println "WARNING: Running george.simple-editor/-main" (-main))

;(run "(println \"(+ 2 3)\"))\n(+ 4 (+ 2 3))")
;(run "(println (+ 2 3)))\n((+ 4 5)")



nil