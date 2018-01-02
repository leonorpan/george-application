; Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "Misc file handling utilities and UI elements, for Editors and Files."}
  george.application.file

  (:require
    [clojure.java.io :as cio]
    [george.javafx :as fx]
    [george.util.file :as guf]
    [george.util.time :as t])
  (:import
    [java.io File]
    [javafx.stage FileChooser]))


(def GEORGE_DOCUMENT_DIR
  (cio/file guf/MY_DOCUMENTS "George"))


(def TEMP_SWAP_DIR
  (cio/file GEORGE_DOCUMENT_DIR "#swaps#"))


(defonce clj-filechooser ^FileChooser (apply fx/filechooser fx/FILESCHOOSER_FILTERS_CLJ))


(defn select-file-for-open
  "Returns (an existing) selected file or nil"
  [owner]
  (when-let [^File f
             (-> (doto clj-filechooser (.setTitle "Select a file ..."))
                 (.showOpenDialog  owner))]
    ;; leave the filechooser in a useful location
    (.setInitialDirectory clj-filechooser (.getParentFile f))
    ;; then return the selected file
    f))


(defn create-file-for-save
  "Returns a new created file or nil"
  [owner]
  (when-let [^File f
             (-> (doto clj-filechooser (.setTitle "Save file as ..."))
                 (.showSaveDialog owner))]
    (.setInitialDirectory clj-filechooser (.getParentFile f))
    f))


(def ^:const swap-re #"#.+#")

(defn swap?
  "Returns true if this is a swap-file (i.e. #filename#)"
  [^String n]
  (boolean (re-matches swap-re n)))

;(println (swap? "#xxx#"))
;(println (swap? "##"))
;(println (swap? "#xxx"))
;(println (swap? "#xxx#x"))


(defn swap-wrap
  "Returns the passed-in name as a swap-file name (a la emacs), but only if it isn't already so."
  [^String n]
  (if-not (swap? n)
    (format "#%s#" n)
    n))

;(println (swap-wrap "xxx"))
;(println (swap-wrap "#xxx"))
;(println (swap-wrap "#xxx#"))


(defn swap-unwrap
  "Returns the passed-in swap-file-name (a la emacs) as a stripped name, but only if it is a swap-file-name."
  [^String n]
  (if (swap? n)
    (subs n 1 (dec (count n)))
    n))

;(println (swap-unwrap "#xxx#"))
;(println (swap-unwrap "#xxx##"))
;(println (swap-unwrap "#xxx"))


(defn create-temp-swap
  "Creates an swap-file in default document directory for such, then returns the file."
  []
  (let [iso (t/now-iso-basic)
        f (cio/file TEMP_SWAP_DIR (swap-wrap iso))]
    (guf/ensure-file f)))


(defn create-swap
  "Creates a swap-file in same location as f, and returns it"
  [f]
  (let [d (guf/parent-dir f)
        n (.getName f)
        f (cio/file d (swap-wrap n))]
    (guf/ensure-file f)))


(defn save-swap
  "Renames the swapf to f. This results in f now containing the new content, and swap no longer existing."
  [^File swapf ^File f]
  (.renameTo swapf f))
