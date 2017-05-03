;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.util
  (:require [clojure.pprint :as cpp])
  (:import (java.util UUID)))



(defn pprint-str
  "returns a pprint-formated str"
  [data]
  (cpp/write data :stream nil))
;; is this better or worse than (with-out-str (cpp data))


(defn uuid
  "Returns a new UUID string."
  []
  (str (UUID/randomUUID)))


;; from Versions.java in george-client
(def IS_MAC  (-> (System/getProperty "os.name") .toLowerCase (.contains "mac")))

(def IS_WINDOWS (-> (System/getProperty "os.name") .toLowerCase (.contains "windows")))


(def SEP java.io.File/separator)
(def PSEP java.io.File/pathSeparator)


(def SHORTCUT_KEY (if IS_MAC "CMD" "CTRL"))


(defn ensure-newline [obj]
  "ensures that the txt ends with a new-line"
  (let [txt (if (nil? obj) "nil" (str obj))]
    (if (= "\n" (last txt))
      txt
      (str txt \newline))))
