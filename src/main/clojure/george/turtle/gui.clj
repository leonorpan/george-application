;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software. 

(ns
  ^{:doc "Contains elements not directly related to the turtle api itself, but necessary for working with Turtle Geometry.
  This includes facilities for handling stage, menus and more."}
  george.turtle.gui
  (:require
    [george.javafx.java :as fxj]
    [george.javafx :as fx]
    [clojure.java.io :as cio])
  (:import
    [javafx.scene.control ContextMenu MenuItem]
    [javafx.scene.input Clipboard DataFormat ClipboardContent]
    [java.io FilenameFilter File ByteArrayOutputStream]
    [javafx.embed.swing SwingFXUtils]
    [javax.imageio ImageIO]
    [java.nio ByteBuffer]
    [javafx.scene.image WritableImage]))



(declare copy-screenshot-to-clipboard)
(declare save-screenshot-to-file)



(def SCREENSHOT_BASE_FILENAME "TG_screenshot")


(defn- parse-int [s]
  (if-let [number (re-find #"\d+" s)]
    (Integer/parseInt number)
    0))



(defn- find-next-file-numbering [dir]
  (->> (.listFiles
         (cio/file dir)
         (reify FilenameFilter
           (accept [_ _ name]
             (boolean (re-find (re-pattern SCREENSHOT_BASE_FILENAME) name)))))
       (seq)
       (map #(.getName %))
       (map parse-int)
       (remove nil?)
       (#(if (empty? %) '(0) %))
       ^long (apply max)
       (inc)))



(defn- write-image-to-file
  "Writes image to file (as '.png')"
  [image file]
  (cio/make-parents file)
  (ImageIO/write
    (SwingFXUtils/fromFXImage image nil)
    "png"
    file))


(def CB (fx/now (Clipboard/getSystemClipboard)))


(defn- print-clipboard-content
  "For dev/test purposes."
  []
  (println "  ## CB content types:" (fx/now (.getContentTypes CB)))
  ;(fx/now (.getString CB)))
  (fx/now (.getImage CB)))


(defn- write-image-to-tempfile [image]
  (let [file (File/createTempFile (str SCREENSHOT_BASE_FILENAME "_") ".png")]
    (when (write-image-to-file image file)
      file)))


(defn- image->png-bytebuffer [^WritableImage im]
  (let [baos (ByteArrayOutputStream.)
        _ (ImageIO/write (SwingFXUtils/fromFXImage im nil) "png" baos)
        res (ByteBuffer/wrap (.toByteArray (doto baos .flush)))]
    (.close baos)
    res))


(defn- put-image-on-clipboard [image text-repr]
  (let [png-mime "image/png"
        png
        (if-let [df (DataFormat/lookupMimeType png-mime)]
          df (DataFormat. (fxj/vargs png-mime)))
        tempfile
        (write-image-to-tempfile image)
        cc
        (doto (ClipboardContent.)
          (.putImage image)
          ;(.put png (image->png-bytebuffer image))  ;; doesn't work for some reason!
          (.putFiles [tempfile])
          (.putFilesByPath [(str tempfile)])
          (.putString text-repr))]
    (fx/now (.setContent CB cc))))




(defn- ^WritableImage snapshot
  ""
  [scene]
  (let [[w h] (fx/WH scene)]
    (.snapshot scene (WritableImage. w h))))


(defn- ^WritableImage screenshot [scene]
  (fx/now (-> scene snapshot)))


"A fileshooser-object is instanciated once pr session. It remembers the previous location.
One might in future choose to save the 'initial directory' so as to return user to same directory across sessions."
(defonce screenshot-filechooser
         (apply fx/filechooser fx/FILESCHOOSER_FILTERS_PNG))


(defn- build-filename-suggestion [dir]
  (format "%s%s.png"
          SCREENSHOT_BASE_FILENAME
          (find-next-file-numbering dir)))



(defn- ^File choose-target-file
  "If user selects a location and a file-name, then a file object is returned. Else nil.
  (A file hasn't been created yet. Only name and location chosen. The file is created when it is written to.)"
  []
  (let [initial-dir
        (if-let [dir (.getInitialDirectory screenshot-filechooser)]
          dir
          (cio/file (System/getProperty "user.home")))

        suggested-filename
        (build-filename-suggestion initial-dir)]

    (when-let [file (-> (doto screenshot-filechooser
                          (.setTitle "Save sreenshot as ...")
                          (.setInitialFileName suggested-filename))
                        (.showSaveDialog nil))]

      ;; If a different directory has been chosen, we want the filechooser to remember it:
      (.setInitialDirectory screenshot-filechooser (.getParentFile file))
      ;; Handling of potential overwrite of file is buildt into filechooser.
      file)))

(defn- save-screenshot-to-file [scene]
  (when-let [file (choose-target-file)]
    (write-image-to-file (screenshot scene) file)))


(defn- copy-screenshot-to-clipboard [scene]
  (put-image-on-clipboard (screenshot scene) (format "<%s>" SCREENSHOT_BASE_FILENAME)))



(defn set-cm-menu-on-stage [stage]
  (let [scene (.getScene stage)
        root (.getRoot scene)
        cm
        (ContextMenu.
          (fxj/vargs
            (doto (MenuItem. "Copy screenshot to clipboard")
              (.setOnAction (fx/event-handler-2 [_ e] (copy-screenshot-to-clipboard scene))))
            (doto (MenuItem. "Save screenshot to file ...")
              (.setOnAction (fx/event-handler-2 [_ e] (save-screenshot-to-file scene))))))

        cm-handler
        (fx/event-handler-2 [_ e]
                            (.show cm root
                                   (.getScreenX e)
                                   (.getScreenY e)))]

    (.setOnContextMenuRequested scene cm-handler)
    stage))