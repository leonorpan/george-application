;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "Misc file handling utilities and UI elements, for Editors and Files."}
  george.application.file

  (:require
    [clojure.java.io :as cio]
    [george.javafx :as fx]
    [george.util.file :as guf]
    [george.util.time :as t]
    [george.application.output :refer [oprintln]]
    [george.application.launcher :as appl]
    [george.javafx.java :as fxj])
  (:import
    [java.io File IOException]
    [java.nio.file Files  StandardCopyOption]))


(def GEORGE_DOCUMENT_DIR
  (cio/file guf/USER_DOCUMENTS "George"))


(def TEMP_SWAP_DIR
  (cio/file GEORGE_DOCUMENT_DIR "#swaps#"))

(def missing-thing
  {:folder ["Directory missing!" "previous folder location"]
   :swap-dir ["File creation failed!" "file's directory"]
   :swap-file ["Save failed!" "swap file"]})


(defn show-something-missing-alert [what]
  (let [[title thing] (missing-thing what)]
    (fx/alert
      :title title
      :header (format "The %s has gone missing!" thing)
      :content "Don't know what to do about that, exactly. :-( \nCan you fix it?"
      :owner (appl/current-application-stage)
      :type :error)))


(defonce clj-filechooser
         (doto (apply fx/filechooser fx/FILESCHOOSER_FILTERS_CLJ)
               (.setInitialDirectory guf/USER_DOCUMENTS)))


(defn select-file-for-open
  "Returns (an existing) selected file or nil"
  []
  (let [fc (doto clj-filechooser (.setTitle "Select a file ..."))
        owner (appl/current-application-stage)]
    (when-let [^File f
               (try (.showOpenDialog fc owner)
                    (catch IllegalArgumentException e
                           (show-something-missing-alert :folder)
                           (.setInitialDirectory fc guf/USER_DOCUMENTS)
                           (select-file-for-open)))]
      ;; leave the filechooser in a useful location
      (.setInitialDirectory clj-filechooser (.getParentFile f))
      ;; then return the selected file
      f)))


(defn create-file-for-save
  "Returns a new created file or nil"
  []
  (let [fc (doto clj-filechooser (.setTitle "Save file as ..."))
        owner (appl/current-application-stage)]
    (when-let [^File f
               (try (.showSaveDialog fc owner)
                    (catch IllegalArgumentException e
                      (show-something-missing-alert :folder)
                      (.setInitialDirectory fc guf/USER_DOCUMENTS)
                      (create-file-for-save)))]

      (.setInitialDirectory clj-filechooser (.getParentFile f))
      f)))


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


(defn parent-dir-exists-or-alert-print [^File d alert?]
  (if (.exists d)
      true
      (fx/now
        (when alert?
          (show-something-missing-alert :swap-dir))
        (oprintln :err "Directory missing: " (str d))
        false)))


(defn create-swap
  "Creates a swap-file in same location as f, and returns it.
  If parent-dir doesn't exist, then it returns nil."
  [f alert?]
  (let [d (guf/parent-dir f)
        n (.getName f)
        f (cio/file d (swap-wrap n))]
    (when (parent-dir-exists-or-alert-print d alert?)
          (guf/ensure-file f))))


(defn swap-file-exists-or-alert-print [^File swapf alert?]
  (if (and swapf (.exists swapf))
    true
    (fx/now
      (when alert?
        (show-something-missing-alert :swap-file))
      (oprintln :err "Swap file missing: " (str swapf))
      false)))


(defn save-swap
  "Renames the swapf to f. This results in f now containing the new content, and swap no longer existing.
  Returns true if swap was successful, else false."
  [^File swapf ^File f]
  (if (swap-file-exists-or-alert-print swapf true)
    ;(.renameTo swapf f) ;; doesn't work on Windows if file exists.
    (try (boolean (Files/move (.toPath swapf) (.toPath f) (fxj/vargs StandardCopyOption/REPLACE_EXISTING StandardCopyOption/ATOMIC_MOVE)))
         (catch IOException e (.printStackTrace e)))
    false))