;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.util.file
  (:require
    [clojure.java.io :as cio])
  (:import
    [java.io File]
    [java.nio.file Files]))


(defn ^File user-documents
 ([]
  (let [user-home (System/getProperty "user.home")
        dir (cio/file  user-home "Documents")  ;; Windows now uses "Documents"
        dir (if (.exists dir) dir
                              (user-documents "My Documents"))]  ;; In case older installation
    (if (.exists dir) dir
                      (user-documents ""))))  ;; OK. Lets go with naked user-dir
 ([name]
  (cio/file (System/getProperty "user.home") name)))


(def USER_DOCUMENTS (user-documents))


(defn ^File parent-dir
  "Returns the dir containing the file if it is named in the file-object, else nil"
  [^File f]
  (.getParentFile f))


(defn ^Boolean create-dirs
  "Returns true if any parent dirs were created."
  [^File f]
  (-> f .getParentFile .mkdirs))


(defn ^Boolean create-file
  "Returns true if the file was created"
  [^File f]
  (.createNewFile f))


(defn ^File ensure-dirs
  "Returns the file, creating any dirs if necessary."
  [^File f]
  (doto f (create-dirs)))


(defn ^File ensure-file
  "Returns the file, creating any dirs and he file itself if necessary."
  [^File f]
  (doto f (ensure-dirs) (create-file)))


(defn delete-file
  "Deletes file if it exists. Returns true if a file was deleted."
  [^File f]
  (-> f .toPath Files/deleteIfExists))



